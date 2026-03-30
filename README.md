# Village Quest

This repository is maintained as a two-version monorepo.

- `1.21.11/` contains the legacy maintenance line.
- `26.1/` contains the newer Minecraft 26.1 maintenance line.

Both folders are intentionally kept independent. Shared fixes can be ported between them, but code should not be copied blindly across versions because mappings, APIs, Java targets, and client hooks differ.

For local run and build commands, see [TEST_COMMANDS.md](TEST_COMMANDS.md).
