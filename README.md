# Village Quest

This repository currently contains three active version folders of `Village Quest`.

Current stable release: `1.22.8` on all three maintained lines.

- `26.2/` is the active Minecraft `26.2` Mojang-mapped work line.
- `26.1.2/` is the last shipped modern `26.1.2` baseline.
- `1.21.11/` is the legacy Yarn line.

Each folder is a self-contained Gradle project. Build and run the folder you actually want to work on.
Port behavior deliberately between lines; do not copy code blindly because mappings, APIs, Java targets, and client hooks differ.
