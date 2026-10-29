# api/views.py
from .responses import APIResponse
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.permissions import IsAdminUser, AllowAny, IsAuthenticated
from .throttles import (
    LoginRateThrottle, OTPRateThrottle, client_ip, OTPSendPhoneThrottle,
    OTPVerifyPhoneThrottle, OTPVerifyThrottle,
)
from .services.otp import send_otp as otp_send, verify_otp as otp_verify
from rest_framework_simplejwt.tokens import RefreshToken
from django.conf import settings
from django.contrib.auth.hashers import check_password, make_password
from django.utils import timezone
from datetime import timedelta
from .serializers import (
    RegisterSerializer, LoginSerializer, UserSerializer, 
    UpdateUserSerializer, ForgotPasswordSendOTPSerializer,
    ForgotPasswordVerifyOTPSerializer, ResetPasswordSerializer,
    LogoutSerializer, SendOTPSerializer, VerifyOTPSerializer
)
from .models import User
from .permissions import HasActiveSubscription
from django.core.cache import cache
from accounting.models import Plan, Subscription # اضافه شد
import secrets


OTP_EXPIRY = 120
REGISTER_TOKEN_EXPIRY = 600
RESET_TOKEN_EXPIRY = 300

# Password login: after LOGIN_MAX_FAILURES wrong passwords for one phone the
# account refuses passwords for LOGIN_LOCKOUT_SECONDS, whatever IP they come
# from. OTP login keeps working, so the real owner is never locked out — the
# lockout can be triggered by anyone who knows the number.
LOGIN_MAX_FAILURES = 5
LOGIN_LOCKOUT_SECONDS = 15 * 60

# Hashed once and checked against when the phone has no account, so a wrong
# number costs the same hashing time as a wrong password — otherwise the
# response time alone says whether a number is registered.
_DUMMY_PASSWORD_HASH = make_password('noobatyar-timing-equaliser')


def _login_fail_key(phone):
    return f'login:fail:{phone}'


def _password_change_cooldown():
    return timedelta(hours=getattr(settings, 'PASSWORD_CHANGE_COOLDOWN_HOURS', 24))


def _password_change_blocked(user):
    """The Persian refusal if ``user`` changed their password too recently,
    else None."""
    if not user.password_changed_at:
        return None
    if timezone.now() - user.password_changed_at >= _password_change_cooldown():
        return None
    hours = int(_password_change_cooldown().total_seconds() // 3600)
    return f'رمز این حساب در {hours} ساعت گذشته تغییر کرده است؛ هر {hours} ساعت فقط یک بار می‌توان رمز را تغییر داد.'


def _revoke_all_sessions(user):
    """Blacklist every refresh token the user holds, so a password reset also
    signs out whoever else was in the account."""
    from rest_framework_simplejwt.token_blacklist.models import BlacklistedToken, OutstandingToken
    for token in OutstandingToken.objects.filter(user=user):
        BlacklistedToken.objects.get_or_create(token=token)


def get_tokens(user):
    refresh = RefreshToken.for_user(user)
    return {
        'refresh': str(refresh),
        'access': str(refresh.access_token),
    }


def _extract_error(errors: dict) -> str:
    """اولین پیام خطا رو از serializer errors بیرون میکشه"""
    for field, messages in errors.items():
        if isinstance(messages, list) and messages:
            return f"{messages[0]}"
        if isinstance(messages, str):
            return messages
    return 'اطلاعات نامعتبر'


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([OTPRateThrottle, OTPSendPhoneThrottle])
def send_otp_view(request):
    serializer = SendOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    phone = serializer.validated_data['phone']

    # Delegates to the Redis-backed service:
    #   - checks 3-min rate limit
    #   - checks the per-phone daily send cap
    #   - generates OTP, stores in cache
    #   - sends the SMS synchronously (blocks up to ~10 s on the provider call;
    #     a later outbox phase will move this to a background worker)
    result = otp_send(phone, ip=client_ip(request))
    if not result['success']:
        return APIResponse.error(result.get('error', 'خطا در ارسال کد'))

    return APIResponse.success(
        data={'expires_in': 180},
        message='کد تأیید ارسال شد'
    )


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([OTPVerifyThrottle, OTPVerifyPhoneThrottle])
def verify_otp_view(request):
    serializer = VerifyOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    phone = serializer.validated_data['phone']
    code  = str(serializer.validated_data['code'])

    # Delegates to the service: tracks per-code attempts
    result = otp_verify(phone, code)
    if not result['success']:
        return APIResponse.error(result.get('error', 'کد نامعتبر است'))

    user = User.objects.filter(phone=phone).first()
    if user:
        # Existing user → direct login
        return APIResponse.success(
            data={
                'is_registered': True,
                'user': UserSerializer(user).data,
                'tokens': get_tokens(user)
            },
            message='ورود موفق'
        )
    else:
        # New user → issue a short-lived register token
        register_token = secrets.token_urlsafe(32)
        cache.set(f'register_token:{phone}', register_token, timeout=REGISTER_TOKEN_EXPIRY)
        return APIResponse.success(
            data={
                'is_registered': False,
                'register_token': register_token,
                'expires_in': REGISTER_TOKEN_EXPIRY
            },
            message='شماره تایید شد. لطفا ثبت نام را تکمیل کنید.'
        )


@api_view(['POST'])
@permission_classes([AllowAny])
def register_view(request):
    serializer = RegisterSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    password = serializer.validated_data.get('password') or ''
    register_token = serializer.validated_data.get('register_token') or ''
    name = serializer.validated_data['name']

    # مسیر وب: ثبت‌نام فقط با توکنی که پس از تأیید OTP صادر شده. بدون این بررسی
    # هر کسی می‌توانست بدون تأیید شماره، برای شماره دلخواه حساب بسازد.
    if register_token:
        expected = cache.get(f'register_token:{phone}')
        if not expected or not secrets.compare_digest(str(expected), register_token):
            return APIResponse.error('توکن تأیید شماره نامعتبر یا منقضی شده است')
        cache.delete(f'register_token:{phone}')

    user = User.objects.create_user(
        phone=phone,
        password=password or None,  # None → رمز غیرقابل استفاده (ورود فقط با OTP)
        name=name
    )

    # فعال‌سازی خودکار پلن آزمایشی برای کاربر جدید
    trial_plan = Plan.objects.filter(price=0, is_active=True).first()
    if trial_plan:
        Subscription.objects.create(
            user=user,
            plan=trial_plan,
            ends_at=trial_plan.get_end_date()
        )

    return APIResponse.success(
        data={
            'user': UserSerializer(user).data,
            'tokens': get_tokens(user)
        },
        message='ثبت‌نام موفق',
        status=201
    )


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([LoginRateThrottle])
def login_view(request):
    serializer = LoginSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    fail_key = _login_fail_key(phone)
    if cache.get(fail_key, 0) >= LOGIN_MAX_FAILURES:
        return APIResponse.error(
            'به‌خاطر چند رمز اشتباه، ورود با رمز برای این شماره ۱۵ دقیقه بسته شد. '
            'می‌توانید با کد یکبار مصرف وارد شوید.',
            code=429,
        )

    user = User.objects.filter(phone=phone).first()
    password_ok = check_password(
        serializer.validated_data['password'],
        user.password if user else _DUMMY_PASSWORD_HASH,
    )
    if not user or not password_ok or not user.is_active:
        # Counted for unknown numbers too: the lockout must not behave
        # differently for registered ones, or it becomes an existence test.
        try:
            cache.add(fail_key, 0, timeout=LOGIN_LOCKOUT_SECONDS)
            cache.incr(fail_key)
        except ValueError:
            cache.set(fail_key, 1, timeout=LOGIN_LOCKOUT_SECONDS)
        return APIResponse.unauthorized('شماره یا رمز اشتباه است')

    cache.delete(fail_key)

    return APIResponse.success(
        data={
            'user': UserSerializer(user).data,
            'tokens': get_tokens(user)
        },
        message='ورود موفق'
    )


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout_view(request):
    serializer = LogoutSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    try:
        token = RefreshToken(serializer.validated_data['refresh'])
        token.blacklist()
        return APIResponse.success(message='خروج موفق')
    except Exception:
        return APIResponse.error('توکن نامعتبر است')


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([OTPRateThrottle, OTPSendPhoneThrottle])
def forgot_password_send_otp(request):
    serializer = ForgotPasswordSendOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']

    if not User.objects.filter(phone=phone).exists():
        # Don't reveal whether the phone is registered (account enumeration).
        return APIResponse.success(message='اگر شماره ثبت شده باشد، کد ارسال می‌شود')

    # Reuse the Redis-backed OTP service (cooldown + send caps).
    # It stores the code internally and is verified later via otp_verify().
    result = otp_send(phone, ip=client_ip(request))
    if not result['success']:
        return APIResponse.error(result.get('error', 'خطا در ارسال پیامک'))

    return APIResponse.success(
        data={'expires_in': OTP_EXPIRY},
        message='کد تأیید ارسال شد'
    )


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([OTPVerifyThrottle, OTPVerifyPhoneThrottle])
def forgot_password_verify_otp(request):
    serializer = ForgotPasswordVerifyOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    code = str(serializer.validated_data['code'])

    # Verify against the same OTP service used to send it.
    result = otp_verify(phone, code)
    if not result['success']:
        return APIResponse.error(result.get('error', 'کد نامعتبر یا منقضی شده است'))

    # Said here, after the code proved the caller holds this phone — not at
    # the send step, where it would tell anyone whether a number is
    # registered. The reset step checks again; this is only the early answer.
    user = User.objects.filter(phone=phone).first()
    blocked = user and _password_change_blocked(user)
    if blocked:
        return APIResponse.error(blocked, code=429)

    reset_token = secrets.token_urlsafe(32)
    cache.set(f'reset_token:{phone}', reset_token, timeout=RESET_TOKEN_EXPIRY)

    return APIResponse.success(
        data={'reset_token': reset_token, 'expires_in': RESET_TOKEN_EXPIRY},
        message='کد تأیید شد'
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def forgot_password_reset(request):
    serializer = ResetPasswordSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    reset_token = serializer.validated_data['reset_token']
    new_password = serializer.validated_data['new_password']

    cached_token = cache.get(f'reset_token:{phone}')
    if not cached_token or cached_token != reset_token:
        return APIResponse.error('توکن نامعتبر یا منقضی شده است')

    user = User.objects.filter(phone=phone).first()
    if not user:
        return APIResponse.error('کاربر یافت نشد', code=404)

    blocked = _password_change_blocked(user)
    if blocked:
        return APIResponse.error(blocked, code=429)

    user.set_password(new_password)
    user.password_changed_at = timezone.now()
    user.save(update_fields=['password', 'password_changed_at'])
    cache.delete(f'reset_token:{phone}')
    cache.delete(_login_fail_key(phone))
    _revoke_all_sessions(user)

    return APIResponse.success(message='رمز عبور با موفقیت تغییر کرد')




@api_view(['GET', 'PATCH', 'DELETE'])
@permission_classes([IsAuthenticated])
def user_detail(request, pk):
    # Your own account: always. Someone else's: only with the Django model
    # permission for that exact action — the same ones the admin roles grant
    # (core/management/commands/setup_admin_roles.py). This used to be plain
    # is_staff, which let any panel role (Support, Finance, …) read, edit or
    # delete any user through the API, whatever the admin itself allowed it.
    if request.user.pk != pk:
        needed = {'GET': 'api.view_user', 'PATCH': 'api.change_user', 'DELETE': 'api.delete_user'}[request.method]
        if not request.user.has_perm(needed):
            return APIResponse.error('دسترسی ندارید', code=403)
    
    user = User.objects.filter(pk=pk).first()
    if not user:
        return APIResponse.error('کاربر پیدا نشد', code=404)

    if request.method == 'GET':
        return APIResponse.success(data=UserSerializer(user).data)

    if request.method == 'PATCH':
        serializer = UpdateUserSerializer(user, data=request.data, partial=True)
        if not serializer.is_valid():
            return APIResponse.error(
                message=_extract_error(serializer.errors)
            )
        serializer.save()
        return APIResponse.success(
            data=UserSerializer(user).data,
            message='پروفایل به‌روز شد'
        )

    if request.method == 'DELETE':
        user.delete()
        return APIResponse.success(message='کاربر حذف شد')


