from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parent
SOURCE_DIR = ROOT / "source"
TRANSPARENT_DIR = ROOT / "transparent"
COMPONENT_DIR = ROOT / "components"
PREVIEW_DIR = ROOT / "previews"
TEXTURE_DIR = ROOT.parents[1] / "src" / "main" / "resources" / "assets" / "village-quest" / "textures" / "item"

ITEMS = (
    "magic_shard",
    "starreach_ring",
    "merchant_seal",
    "shepherd_flute",
    "apiarists_smoker",
    "caravan_ledger",
    "roadwarden_horn",
)

PIXEL_LETTERS = {
    "N": ("101", "111", "111", "111", "101"),
    "E": ("111", "100", "110", "100", "111"),
    "S": ("111", "100", "111", "001", "111"),
    "W": ("101", "101", "111", "111", "101"),
}


def remove_chroma_key(source: Image.Image) -> Image.Image:
    image = source.convert("RGBA")
    corners = (
        image.getpixel((0, 0)),
        image.getpixel((image.width - 1, 0)),
        image.getpixel((0, image.height - 1)),
        image.getpixel((image.width - 1, image.height - 1)),
    )
    key = tuple(sum(pixel[channel] for pixel in corners) / len(corners) for channel in range(3))
    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result_pixels = result.load()
    source_pixels = image.load()

    transparent_distance = 24.0
    opaque_distance = 108.0
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, _ = source_pixels[x, y]
            distance = math.sqrt((r - key[0]) ** 2 + (g - key[1]) ** 2 + (b - key[2]) ** 2)
            alpha = max(0.0, min(1.0, (distance - transparent_distance) / (opaque_distance - transparent_distance)))
            if alpha <= 0.0:
                continue
            if alpha >= 0.995:
                result_pixels[x, y] = (r, g, b, 255)
                continue

            # Unmix the flat key color instead of turning violet/blue edges gray.
            foreground = []
            for observed, key_channel in zip((r, g, b), key):
                value = (observed - ((1.0 - alpha) * key_channel)) / alpha
                foreground.append(max(0, min(255, round(value))))
            result_pixels[x, y] = (*foreground, round(alpha * 255))
    return result


def alpha_bbox(image: Image.Image, threshold: int = 20) -> tuple[int, int, int, int]:
    alpha = image.getchannel("A").point(lambda value: 255 if value >= threshold else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("Generated master contains no visible subject")
    return bbox


def fit_to_canvas(
    image: Image.Image,
    canvas_size: int,
    subject_size: int,
) -> tuple[Image.Image, float, tuple[int, int], tuple[int, int, int, int]]:
    bbox = alpha_bbox(image)
    cropped = image.crop(bbox)
    scale = min(subject_size / cropped.width, subject_size / cropped.height)
    resized_size = (
        max(1, round(cropped.width * scale)),
        max(1, round(cropped.height * scale)),
    )
    resized = cropped.resize(resized_size, Image.Resampling.LANCZOS)
    offset = ((canvas_size - resized.width) // 2, (canvas_size - resized.height) // 2)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.alpha_composite(resized, offset)
    return canvas, scale, offset, bbox


def finish_sprite(image: Image.Image) -> Image.Image:
    sharpened = image.filter(ImageFilter.UnsharpMask(radius=0.65, percent=175, threshold=2))
    alpha = sharpened.getchannel("A").point(lambda value: 255 if value >= 92 else 0)
    rgb = sharpened.convert("RGB").quantize(
        colors=48,
        method=Image.Quantize.MAXCOVERAGE,
        dither=Image.Dither.NONE,
    ).convert("RGB")
    result = rgb.convert("RGBA")
    result.putalpha(alpha)
    return result


def draw_pixel_letter(
    image: Image.Image,
    letter: str,
    center_x: int,
    center_y: int,
    color: tuple[int, int, int, int],
    scale: int = 2,
) -> None:
    pattern = PIXEL_LETTERS[letter]
    width = len(pattern[0]) * scale
    height = len(pattern) * scale
    start_x = round(center_x - width / 2)
    start_y = round(center_y - height / 2)
    draw = ImageDraw.Draw(image)
    shadow = (28, 21, 18, 230)
    for row, bits in enumerate(pattern):
        for column, bit in enumerate(bits):
            if bit != "1":
                continue
            x0 = start_x + column * scale
            y0 = start_y + row * scale
            draw.rectangle((x0 + 1, y0 + 1, x0 + scale, y0 + scale), fill=shadow)
            draw.rectangle((x0, y0, x0 + scale - 1, y0 + scale - 1), fill=color)


def draw_cardinal_badge(
    image: Image.Image,
    letter: str,
    center_x: int,
    center_y: int,
    color: tuple[int, int, int, int],
    accent: tuple[int, int, int, int],
) -> None:
    draw = ImageDraw.Draw(image)
    half_width = 5
    half_height = 7
    draw.rectangle(
        (center_x - half_width, center_y - half_height, center_x + half_width, center_y + half_height),
        fill=(23, 18, 15, 245),
    )
    draw.rectangle(
        (center_x - half_width + 1, center_y - half_height + 1, center_x + half_width - 1, center_y + half_height - 1),
        fill=accent,
    )
    draw.rectangle(
        (center_x - half_width + 2, center_y - half_height + 2, center_x + half_width - 2, center_y + half_height - 2),
        fill=(11, 43, 72, 255),
    )
    draw_pixel_letter(image, letter, center_x, center_y, color)


def circular_layer(image: Image.Image, center: tuple[float, float], radius: float, inside: bool) -> Image.Image:
    layer = image.copy()
    pixels = layer.load()
    cx, cy = center
    radius_squared = radius * radius
    for y in range(layer.height):
        for x in range(layer.width):
            is_inside = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) <= radius_squared
            if is_inside != inside:
                r, g, b, _ = pixels[x, y]
                pixels[x, y] = (r, g, b, 0)
    return layer


def build_compass() -> None:
    master_path = SOURCE_DIR / "surveyors_compass_master.png"
    cleaned = remove_chroma_key(Image.open(master_path))
    TRANSPARENT_DIR.mkdir(parents=True, exist_ok=True)
    cleaned.save(TRANSPARENT_DIR / "surveyors_compass.png")

    base, scale, offset, bbox = fit_to_canvas(cleaned, 256, 236)
    # Coordinates measured once on the approved generated master. Keeping them here
    # makes the 32 frames reproducible and prevents independent frame drift.
    source_center = (627.0, 655.0)
    source_radius = 300.0
    center = (
        offset[0] + (source_center[0] - bbox[0]) * scale,
        offset[1] + (source_center[1] - bbox[1]) * scale,
    )
    radius = source_radius * scale

    dial = circular_layer(base, center, radius - 1.5, inside=True)
    cardinal_offset = radius - 13
    draw_cardinal_badge(
        dial,
        "N",
        round(center[0]),
        round(center[1] - cardinal_offset),
        (103, 238, 228, 255),
        (43, 137, 139, 255),
    )
    for letter, x, y in (
        ("E", center[0] + cardinal_offset, center[1]),
        ("S", center[0], center[1] + cardinal_offset),
        ("W", center[0] - cardinal_offset, center[1]),
    ):
        draw_cardinal_badge(
            dial,
            letter,
            round(x),
            round(y),
            (236, 198, 105, 255),
            (128, 88, 34, 255),
        )
    bezel = circular_layer(base, center, radius - 3.0, inside=False)

    COMPONENT_DIR.mkdir(parents=True, exist_ok=True)
    dial.save(COMPONENT_DIR / "surveyors_compass_inner.png")
    bezel.save(COMPONENT_DIR / "surveyors_compass_outer.png")

    neutral = Image.new("RGBA", base.size, (0, 0, 0, 0))
    neutral.alpha_composite(dial)
    neutral.alpha_composite(bezel)
    neutral.save(TEXTURE_DIR / "surveyors_compass.png")

    frames: list[Image.Image] = []
    for index in range(32):
        rotated = dial.rotate(
            -index * 11.25,
            resample=Image.Resampling.BICUBIC,
            center=center,
        )
        rotated = circular_layer(rotated, center, radius - 1.5, inside=True)
        frame = Image.new("RGBA", base.size, (0, 0, 0, 0))
        frame.alpha_composite(rotated)
        frame.alpha_composite(bezel)
        frame.save(TEXTURE_DIR / f"surveyors_compass_{index:02d}.png")
        frames.append(frame)

    preview = Image.new("RGBA", (4 * 272, 2 * 272), (35, 27, 23, 255))
    draw = ImageDraw.Draw(preview)
    for slot, index in enumerate((0, 4, 8, 12, 16, 20, 24, 28)):
        x = (slot % 4) * 272 + 8
        y = (slot // 4) * 272 + 8
        preview.alpha_composite(frames[index], (x, y))
        draw.text((x + 6, y + 232), f"{index:02d} / {index * 11.25:.2f} deg", fill=(236, 213, 163, 255))
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_DIR / "surveyors_compass_frames.png")


def build_item_sprites() -> None:
    TRANSPARENT_DIR.mkdir(parents=True, exist_ok=True)
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    sprites: list[tuple[str, Image.Image]] = []
    for name in ITEMS:
        master_path = SOURCE_DIR / f"{name}_master.png"
        cleaned = remove_chroma_key(Image.open(master_path))
        cleaned.save(TRANSPARENT_DIR / f"{name}.png")
        fitted, _, _, _ = fit_to_canvas(cleaned, 32, 28)
        sprite = finish_sprite(fitted)
        sprite.save(TEXTURE_DIR / f"{name}.png")
        sprites.append((name, sprite))

    preview = Image.new("RGBA", (7 * 128, 160), (35, 27, 23, 255))
    draw = ImageDraw.Draw(preview)
    for index, (name, sprite) in enumerate(sprites):
        scaled = sprite.resize((96, 96), Image.Resampling.NEAREST)
        x = index * 128 + 16
        preview.alpha_composite(scaled, (x, 12))
        draw.text((x, 116), name.replace("_", " "), fill=(236, 213, 163, 255))
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_DIR / "item_set.png")


def main() -> None:
    build_item_sprites()
    build_compass()
    print(f"Built {len(ITEMS)} item sprites and 32 compass frames in {TEXTURE_DIR}")


if __name__ == "__main__":
    main()
