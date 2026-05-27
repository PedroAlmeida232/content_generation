from io import BytesIO
import pytest
from PIL import Image

from app.services.image_processor import resize_to_instagram


def _create_dummy_image(
    width: int,
    height: int,
    mode: str = "RGB",
) -> bytes:
    """Helper para criar bytes de uma imagem de teste."""
    color = (255, 0, 0) if mode == "RGB" else (255, 0, 0, 128)
    img = Image.new(mode, (width, height), color=color)
    buf = BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def test_resize_to_instagram_1_1() -> None:
    # Imagem original retangular vertical (1000x2000)
    original_bytes = _create_dummy_image(1000, 2000)
    processed = resize_to_instagram(original_bytes, "1:1")

    # Validar dimensões de saída
    out_img = Image.open(BytesIO(processed))
    assert out_img.size == (1080, 1080)
    assert out_img.format == "PNG"


def test_resize_to_instagram_4_5() -> None:
    # Imagem original retangular horizontal (2000x1000)
    original_bytes = _create_dummy_image(2000, 1000)
    processed = resize_to_instagram(original_bytes, "4:5")

    # Validar dimensões de saída
    out_img = Image.open(BytesIO(processed))
    assert out_img.size == (1080, 1350)
    assert out_img.format == "PNG"


def test_resize_to_instagram_9_16() -> None:
    # Imagem original quadrada (1000x1000)
    original_bytes = _create_dummy_image(1000, 1000)
    processed = resize_to_instagram(original_bytes, "9:16")

    # Validar dimensões de saída
    out_img = Image.open(BytesIO(processed))
    assert out_img.size == (1080, 1920)
    assert out_img.format == "PNG"


def test_resize_to_instagram_already_correct_ratio() -> None:
    # Imagem original já com tamanho correto (1080x1080)
    original_bytes = _create_dummy_image(1080, 1080)
    processed = resize_to_instagram(original_bytes, "1:1")

    # Validar dimensões de saída
    out_img = Image.open(BytesIO(processed))
    assert out_img.size == (1080, 1080)


def test_resize_to_instagram_invalid_ratio() -> None:
    original_bytes = _create_dummy_image(100, 100)
    with pytest.raises(ValueError) as exc:
        resize_to_instagram(original_bytes, "16:9")
    assert "Proporcao '16:9' nao suportada" in str(exc.value)


def test_resize_to_instagram_corrupted_image() -> None:
    corrupted_bytes = b"not an image file"
    with pytest.raises(ValueError) as exc:
        resize_to_instagram(corrupted_bytes, "1:1")
    assert "Dados de imagem invalidos ou corrompidos" in str(exc.value)


def test_resize_to_instagram_rgba_to_jpeg() -> None:
    # Imagem original com canal alpha (RGBA)
    original_bytes = _create_dummy_image(500, 500, mode="RGBA")
    processed = resize_to_instagram(
        original_bytes,
        "1:1",
        output_format="JPEG",
    )

    out_img = Image.open(BytesIO(processed))
    assert out_img.size == (1080, 1080)
    assert out_img.format == "JPEG"
    assert out_img.mode == "RGB"
