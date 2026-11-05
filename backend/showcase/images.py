"""Server-side validation and recompression of showcase images.

The owner app crops to the target shape and compresses before upload, but the
server never trusts that: every image is re-opened with Pillow, checked against
the shape rule, downscaled to the target size and re-encoded as JPEG, and a
small thumbnail is produced for list screens.

Rules (``ImageSpec``), the same for portfolio and certificates: the owner picks
the frame, so the picture may be square 1:1 (1080x1080), portrait 4:5
(1080x1350) or landscape 4:3 (1080x810), each +-10%. Forcing one shape made
landscape things (most certificates) unusable. Width >= 600, height >= 450.
Images larger than 1080x1350 are scaled down (aspect ratio preserved, no
crop); smaller than the minimum are rejected; an image that is none of the
shapes is rejected rather than silently cropped.
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
    # Allowed width/height ratios: square, portrait 4:5, landscape 4:3.
    ratios: tuple = (1.0, 4 / 5, 4 / 3)
    tolerance: float = 0.10

    def fits(self, ratio):
        return any(abs(ratio / r - 1) <= self.tolerance for r in self.ratios)


SHOWCASE_SPEC = ImageSpec(1080, 1350, 600, 450)
PORTFOLIO_SPEC = CERTIFICATE_SPEC = SHOWCASE_SPEC


def process_image(uploaded, spec: ImageSpec, stem: str):
    """Return ``(main, thumb)`` as ``ContentFile`` objects named ``stem*.jpg``."""
    img = open_upload(uploaded)

    w, h = img.size
    if w < spec.min_width or h < spec.min_height:
        raise ImageRejected(
            f'عکس خیلی کوچک است؛ حداقل {spec.min_width}×{spec.min_height} پیکسل لازم است.')
    if not spec.fits(w / h):
        raise ImageRejected('نسبت عکس درست نیست؛ عکس باید مربع، عمودی ۴:۵ یا افقی ۴:۳ باشد.')

    img = flatten_rgb(img)

    if w > spec.width or h > spec.height:
        img.thumbnail((spec.width, spec.height), Image.LANCZOS)
    main = encode_jpeg(img, f'{stem}.jpg')

    thumb_img = img.copy()
    thumb_img.thumbnail((THUMB_WIDTH, THUMB_WIDTH * 2), Image.LANCZOS)
    thumb = encode_jpeg(thumb_img, f'{stem}_t.jpg')
    return main, thumb


def open_upload(uploaded):
    """Open an upload as a verified, upright Pillow image (raises ImageRejected)."""
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
    return img


def flatten_rgb(img):
    """JPEG has no alpha: transparent areas become white."""
    if img.mode in ('RGBA', 'LA', 'P'):
        rgba = img.convert('RGBA')
        flat = Image.new('RGB', rgba.size, (255, 255, 255))
        flat.paste(rgba, mask=rgba.split()[-1])
        return flat
    return img.convert('RGB')


def encode_jpeg(img, name):
    buf = io.BytesIO()
    img.save(buf, format='JPEG', quality=JPEG_QUALITY, optimize=True)
    return ContentFile(buf.getvalue(), name=name)
