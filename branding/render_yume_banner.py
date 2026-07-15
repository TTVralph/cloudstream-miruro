from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


SCALE = 3
WIDTH = 320 * SCALE
HEIGHT = 180 * SCALE
VIOLET = (155, 92, 255, 255)
BLUE = (85, 200, 255, 255)


def scaled_box(values: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    return tuple(value * SCALE for value in values)


canvas = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
draw = ImageDraw.Draw(canvas)
draw.rounded_rectangle(
    (0, 0, WIDTH - 1, HEIGHT - 1),
    radius=10 * SCALE,
    fill=(5, 5, 5, 255),
)
draw.ellipse(scaled_box((37, 40, 121, 124)), fill=(12, 9, 22, 255))

mark_mask = Image.new("L", (WIDTH, HEIGHT), 0)
mark = ImageDraw.Draw(mark_mask)
mark.arc(
    scaled_box((43, 46, 115, 118)),
    start=218,
    end=520,
    fill=255,
    width=8 * SCALE,
)
mark.line(
    [(64 * SCALE, 62 * SCALE), (80 * SCALE, 80 * SCALE), (95 * SCALE, 60 * SCALE)],
    fill=255,
    width=7 * SCALE,
    joint="curve",
)
mark.line(
    [(80 * SCALE, 80 * SCALE), (78 * SCALE, 103 * SCALE)],
    fill=255,
    width=7 * SCALE,
)

gradient = Image.new("RGBA", (WIDTH, HEIGHT), VIOLET)
pixels = gradient.load()
for y in range(HEIGHT):
    for x in range(WIDTH):
        amount = min(1.0, max(0.0, (x + y * 0.45) / (WIDTH * 0.62)))
        pixels[x, y] = tuple(
            round(VIOLET[channel] + (BLUE[channel] - VIOLET[channel]) * amount)
            for channel in range(4)
        )
canvas.alpha_composite(Image.composite(gradient, Image.new("RGBA", canvas.size), mark_mask))

draw = ImageDraw.Draw(canvas)
title_font = ImageFont.truetype("DejaVuSans-Bold.ttf", 42 * SCALE)
tagline_font = ImageFont.truetype("DejaVuSans.ttf", 13 * SCALE)
title_x = 130 * SCALE
title_y = 52 * SCALE
draw.text((title_x, title_y), "u", font=title_font, fill="white")
draw.text(
    (title_x + draw.textlength("u", font=title_font), title_y),
    "me",
    font=title_font,
    fill=BLUE,
)
draw.text(
    (134 * SCALE, 105 * SCALE),
    "Your world of anime.",
    font=tagline_font,
    fill=(175, 169, 189, 255),
)

output = (
    Path(__file__).resolve().parents[1]
    / "MiruroApp/src/main/res/drawable-nodpi/app_banner.webp"
)
output.parent.mkdir(parents=True, exist_ok=True)
canvas.resize((320, 180), Image.Resampling.LANCZOS).save(
    output,
    "WEBP",
    quality=92,
    method=6,
)
