from __future__ import annotations

import tempfile
from pathlib import Path


def prepare_image_for_model(
    image_path: str,
    *,
    max_dim: int,
    quality: int,
) -> tuple[str, bool]:
    """
    Return a model-friendly image path.

    The original phone photos are large and include EXIF orientation. This
    creates a temporary, EXIF-applied, downscaled JPEG. If Pillow is not
    installed or max_dim <= 0, returns the original path.
    """
    if max_dim <= 0:
        return image_path, False

    try:
        from PIL import Image, ImageOps
    except ImportError:
        return image_path, False

    source = Path(image_path).expanduser()
    with Image.open(source) as image:
        image = ImageOps.exif_transpose(image).convert("RGB")
        image.thumbnail((max_dim, max_dim))

        temp = tempfile.NamedTemporaryFile(
            prefix="math_agent_image_",
            suffix=".jpg",
            delete=False,
        )
        temp_path = temp.name
        temp.close()

        image.save(temp_path, format="JPEG", quality=quality, optimize=True)
        return temp_path, True


def prepare_image_for_ocr(
    image_path: str,
    *,
    max_dim: int,
    quality: int,
) -> tuple[str, bool]:
    return prepare_image_for_model(image_path, max_dim=max_dim, quality=quality)
