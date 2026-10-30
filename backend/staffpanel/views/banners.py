"""بنرها: the owner-app banners (exactly 1200×400, enforced by the model's own
validator) and the one link every banner opens (SiteSetting)."""

from django import forms
from django.db.models import Prefetch
from django.shortcuts import get_object_or_404

from banners.models import BANNER_HEIGHT, BANNER_WIDTH, Banner, BannerCategoryGroup, SiteSetting

from ..access import panel_view, require
from ._common import back, fail, ok, render_panel


class BannerForm(forms.ModelForm):
    """ModelForm, so Banner.clean() (group xor category, end after start) and
    the image field's validate_banner_size run exactly as in the admin."""

    class Meta:
        model = Banner
        fields = ('image', 'group', 'category', 'order', 'starts_at', 'ends_at', 'is_active')
        widgets = {
            'starts_at': forms.DateTimeInput(attrs={'type': 'datetime-local'}),
            'ends_at': forms.DateTimeInput(attrs={'type': 'datetime-local'}),
        }


class LinkForm(forms.ModelForm):
    class Meta:
        model = SiteSetting
        fields = ('banner_link_url',)


@panel_view('banners.view_banner')
def banners(request):
    groups = list(BannerCategoryGroup.objects.prefetch_related(
        Prefetch('banners', queryset=Banner.objects.order_by('order', 'id'))))
    single = list(Banner.objects.filter(group__isnull=True).order_by('category', 'order', 'id'))
    return render_panel(request, 'staffpanel/banners.html', 'banners', {
        'groups': groups, 'single': single,
        'form': BannerForm(), 'link_form': LinkForm(instance=SiteSetting.load()),
        'width': BANNER_WIDTH, 'height': BANNER_HEIGHT,
        'can_add': request.user.has_perm('banners.add_banner'),
        'can_change': request.user.has_perm('banners.change_banner'),
        'can_link': request.user.has_perm('banners.change_sitesetting'),
    }, title='بنرها')


@panel_view(('banners.view_banner', 'banners.add_banner'), methods=('POST',))
def banner_create(request):
    form = BannerForm(request.POST, request.FILES)
    if form.is_valid():
        form.save()
        ok(request, 'بنر اضافه شد.')
    else:
        fail(request, ' '.join(e for errors in form.errors.values() for e in errors))
    return back(request, 'staffpanel:banners')


@panel_view(('banners.view_banner', 'banners.change_banner'), methods=('POST',))
def banner_toggle(request, pk):
    banner = get_object_or_404(Banner, pk=pk)
    banner.is_active = not banner.is_active
    banner.save(update_fields=['is_active'])
    ok(request, 'بنر فعال شد.' if banner.is_active else 'بنر غیرفعال شد.')
    return back(request, 'staffpanel:banners')


@panel_view('banners.view_banner', methods=('POST',))
def banner_link(request):
    require(request.user, 'banners.change_sitesetting')
    form = LinkForm(request.POST, instance=SiteSetting.load())
    if form.is_valid():
        form.save()
        ok(request, 'لینک مشترک بنرها ذخیره شد.')
    else:
        fail(request, 'آدرس لینک معتبر نیست.')
    return back(request, 'staffpanel:banners')
