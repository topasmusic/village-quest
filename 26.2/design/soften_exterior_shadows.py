from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
GUI = ROOT / "src/main/resources/assets/village-quest/textures/gui"
TARGETS = (
    "journal_board.png",
    "trade_route_board.png",
    "pilgrim_board_v2.png",
)
MAX_SHADOW_CHANNEL = 48


def is_exterior_shadow(pixel: tuple[int, int, int, int]) -> bool:
    red, green, blue, alpha = pixel
    return alpha > 0 and max(red, green, blue) <= MAX_SHADOW_CHANNEL


def remove_baked_shadow(path: Path) -> int:
    """Remove the generated exterior matte so runtime can own the shadow.

    The board artwork itself remains untouched. VillageUiTheme renders the
    same soft ambient and lower-right shadow for every screen at runtime.
    """
    with Image.open(path).convert("RGBA") as image:
        pixels = image.load()
        width, height = image.size
        queue: deque[tuple[int, int]] = deque()
        seen: set[tuple[int, int]] = set()

        for x in range(width):
            queue.append((x, 0))
            queue.append((x, height - 1))
        for y in range(height):
            queue.append((0, y))
            queue.append((width - 1, y))

        changed = 0
        while queue:
            x, y = queue.popleft()
            if x < 0 or y < 0 or x >= width or y >= height or (x, y) in seen:
                continue
            seen.add((x, y))
            pixel = pixels[x, y]
            if not is_exterior_shadow(pixel):
                continue
            red, green, blue, alpha = pixel
            pixels[x, y] = (red, green, blue, 0)
            changed += 1
            queue.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))

        image.save(path, optimize=True)
        return changed


def main() -> None:
    for name in TARGETS:
        path = GUI / name
        print(f"{name}: removed {remove_baked_shadow(path)} exterior shadow pixels")


if __name__ == "__main__":
    main()
