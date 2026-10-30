"""Server-side validation and recompression of showcase images.

The owner app crops to the target shape and compresses before upload, but the
server never trusts that: every image is re-opened with Pillow, checked against
the shape rule, downscaled to the target size and re-encoded as JPEG, and a
small thumbnail is produced for list screens.

Rules (``ImageSpec``):
    portfolio    1080x1080 target, ratio 1:1 (+-10%), shortest side >= 600
    certificate  1080x1350 target, ratio 4:5 (+-10%), width >= 600
Images larger than the target are scaled down (aspect ratio preserved, no
crop); smaller than the minimum are rejected; an image that is not
approximately the right shape is rejected rather than silently cropped.
"""
import io
from dataclasses import dataclass

from django.core.files.base import ContentFile
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_UPLOAD_BYTES = 8 * 1024 * 1024
MAX_PIXELS = 40_000_000
THUMB_WIDTH = 360
JPEG_QUALITY = 85


class ImageRejected(ValueError):
    """The upload cannot be used; the message is shown to the owner."""


@dataclass(frozen=True)
class ImageSpec:
    width: int
    height: int
    min_width: int
    min_height: int
    tolerance: float = 0.10

    @property
    def ratio(self):
        return self.width / self.height


PORTFOLIO_SPEC = ImageSpec(1080, 1080, 600, 600)
CERTIFICATE_SPEC = ImageSpec(1080, 1350, 600, 750)


def process_image(uploaded, spec: ImageSpec, stem: str):
    """Return ``(main, thumb)`` as ``ContentFile`` objects named ``stem*.jpg``."""
    if uploaded.size > MAX_UPLOAD_BYTES:
        raise ImageRejected('حجم عکس نباید بیشتر از ۸ مگابایت باشد.')
    try:
        probe = Image.open(uploaded)
        probe.verify()
        uploaded.seek(0)
        img = Image.open(uploaded)
        if img.width * img.height > MAX_PIXELS:
            raise ImageRejected('ابعاد عکس بیش از حد بزرگ است.')
        img = ImageOps.exif_transpose(img)
        img.load()
    except ImageRejected:
        raise
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError, Image.DecompressionBombError):
        raise ImageRejected('فایل انتخاب‌شده عکس معتبر نیست.')

    w, h = img.size
    if w < spec.min_width or h < spec.min_height:
        raise ImageRejected(
            f'عکس خیلی کوچک است؛ حداقل {spec.min_width}×{spec.min_height} پیکسل لازم است.')
    if abs((w / h) / spec.ratio - 1) > spec.tolerance:
        raise ImageRejected(
            f'نسبت عکس درست نیست؛ عکس باید {spec.width}×{spec.height} پیکسل (یا هم‌نسبت آن) باشد.')

    if img.mode in ('RGBA', 'LA', 'P'):
        rgba = img.convert('RGBA')
        flat = Image.new('RGB', rgba.size, (255, 255, 255))
        flat.paste(rgba, mask=rgba.split()[-1])
        img = flat
    else:
        img = img.convert('RGB')

    if w > spec.width or h > spec.height:
        img.thumbnail((spec.width, spec.height), Image.LANCZOS)
    main = _encode(img, f'{stem}.jpg')

    thumb_img = img.copy()
    thumb_img.thumbnail((THUMB_WIDTH, THUMB_WIDTH * 2), Image.LANCZOS)
    thumb = _encode(thumb_img, f'{stem}_t.jpg')
    return main, thumb


def _encode(img, name):
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=JPEG_QUALITY, optimize=True)
    return ContentFile(buf.getvalue(), name=name)
