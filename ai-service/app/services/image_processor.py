from io import BytesIO
from PIL import Image, UnidentifiedImageError

INSTAGRAM_FORMATS = {
    "1:1": (1080, 1080),
    "4:5": (1080, 1350),
    "9:16": (1080, 1920),
}


def resize_to_instagram(
    image_bytes: bytes,
    aspect_ratio: str,
    output_format: str = "PNG",
) -> bytes:
    """Recorta e redimensiona uma imagem para os formatos do Instagram.

    Args:
        image_bytes: Bytes da imagem original.
        aspect_ratio: Proporcao de destino ('1:1', '4:5', '9:16').
        output_format: Formato do arquivo de saida (ex: 'PNG', 'JPEG').

    Returns:
        bytes: Imagem processada.

    Raises:
        ValueError: Se o aspect_ratio for invalido ou se a imagem for
                    corrompida/invalida.
    """
    if aspect_ratio not in INSTAGRAM_FORMATS:
        allowed = ", ".join(INSTAGRAM_FORMATS.keys())
        raise ValueError(
            f"Proporcao '{aspect_ratio}' nao suportada. "
            f"Formatos permitidos: {allowed}"
        )

    try:
        img = Image.open(BytesIO(image_bytes))
        # Força o carregamento da imagem para validar se ela está íntegra
        img.verify()
        # Após verify(), precisamos reabrir porque o arquivo é fechado
        img = Image.open(BytesIO(image_bytes))
    except (UnidentifiedImageError, ValueError, TypeError, OSError) as exc:
        raise ValueError("Dados de imagem invalidos ou corrompidos.") from exc

    target_w, target_h = INSTAGRAM_FORMATS[aspect_ratio]
    target_ratio_val = target_w / target_h

    width, height = img.size
    orig_ratio = width / height

    # 1. Calcular coordenadas de crop centralizado
    if orig_ratio > target_ratio_val:
        # A imagem original eh mais larga que a proporcao de destino
        new_width = int(round(height * target_ratio_val))
        left = max(0, (width - new_width) // 2)
        top = 0
        right = min(width, left + new_width)
        bottom = height
    elif orig_ratio < target_ratio_val:
        # A imagem original eh mais alta que a proporcao de destino
        new_height = int(round(width / target_ratio_val))
        left = 0
        top = max(0, (height - new_height) // 2)
        right = width
        bottom = min(height, top + new_height)
    else:
        # Ja esta na proporcao correta
        left = 0
        top = 0
        right = width
        bottom = height

    # 2. Executar o crop e o resize
    cropped = img.crop((left, top, right, bottom))
    resized = cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)

    # 3. Converter para RGB se salvar como JPEG e com canal alpha
    fmt_upper = output_format.upper()
    if fmt_upper in ("JPEG", "JPG"):
        if resized.mode in ("RGBA", "LA") or (
            resized.mode == "P" and "transparency" in resized.info
        ):
            # Cria fundo branco para preservar areas transparentes
            bg = Image.new("RGB", resized.size, (255, 255, 255))
            bg.paste(
                resized,
                mask=resized.split()[3] if resized.mode == "RGBA" else None,
            )
            resized = bg
        elif resized.mode != "RGB":
            resized = resized.convert("RGB")

    # 4. Salvar no buffer e retornar bytes
    out_buffer = BytesIO()
    resized.save(out_buffer, format=output_format)
    return out_buffer.getvalue()
