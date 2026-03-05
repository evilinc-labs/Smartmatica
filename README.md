# Smartmatica

A standalone Fabric client mod that automatically places blocks from loaded `.litematic` schematics. Built and designed for all, for everyone's sake. I missed a public Litematica printer, didn't you?

## Disclaimer
**THIS IS IN BETA!!**
It isn't perfect, it will make mistakes, but what matters is that it works. We ask that if there are any discrepancies, that you create an issue for bug tracking. In addition to this, you are also more than welcome to make a pull request if you see any places where it can be better!

**Important Things To Know**
- We recommend using Baritone if you want automatic building.
  - The vanilla walking is bare-bones and (probably) broken. The printing functions should work fine without automatic building regardless, however, if there are issues, please make a note and we will investigate.

**If You Fork**
This is under a GPL v3 license, meaning that the fork must be public and must indicate the changes used. You cannot obfuscate the code and you cannot gatekeep. This includes if you implement it into your own client.

If you find a fork or branch that is in violation of the license, please inform us immediately!

## Features

- **Manual Mode**
  - Toggle the printer to place blocks from the loaded schematic within reach
- **AutoBuild Mode**
  - Fully automated building with zone-based progression, walking between build areas
- **Baritone Pathfinding**
  - Integration with [Baritone](https://github.com/cabaletta/baritone) for pathfinding navigation; falls back to vanilla straight-line walking when Baritone is not installed
- **Supply Chest Restocking**
  - Automatically walks to configured supply chests to restock materials mid-build; indexes chest contents (including shulker boxes) for intelligent chest selection
- **Shulker Box Unloading**
  - Automatically places, opens, loots, and breaks shulker boxes grabbed from supply chests; builds temporary platforms when needed and cleans them up afterward
- **Scaffold Cleanup**
  - Tracks blocks placed by Baritone during pathfinding (bridges, pillars) and removes them once the build is complete
- **Self-Correction**
  - Detects misplaced blocks (wrong orientation, wrong type, foreign blocks) and breaks + replaces them automatically
- **Smart Tool Selection**
  - Selects the best tool from inventory when breaking blocks (scaffold cleanup, self-correction) based on mining speed, so you don't have to babysit (often)
- **Block Property Handling**
  - Correct placement of stairs, slabs, trapdoors (half), doors (facing + hinge side), logs (axis), pillars, and other directional blocks
- **Liquid Pass**
  - Places water and lava source blocks **after** all solid blocks are done to avoid breaking Baritone
- **Schematic Detection**
  - Detects and loads `.litematic` files from the Litematica schematic directory
- **Build Checkpoints**
  - Persists build progress to disk so printing can resume after disconnect or restart

## Supported Minecraft Versions

| Minecraft | Fabric Loader | Fabric API | Yarn Mappings | Java |
|-----------|---------------|------------|---------------|------|
| 1.21–1.21.1 | ≥ 0.18.4 | 0.116.9+1.21.1 | 1.21.1+build.3 | 21 |
| 1.21.4 | ≥ 0.18.4 | 0.119.4+1.21.4 | 1.21.4+build.8 | 21 |
| 1.21.5 | ≥ 0.18.4 | 0.128.2+1.21.5 | 1.21.5+build.1 | 21 |
| 1.21.8 | ≥ 0.18.4 | 0.136.1+1.21.8 | 1.21.8+build.1 | 21 |
| 1.21.9–1.21.10 | ≥ 0.18.4 | 0.138.4+1.21.10 | 1.21.10+build.3 | 21 |

## Dependencies

### Required

| Dependency | Version | Purpose |
|------------|---------|---------|
| [Fabric Loader](https://fabricmc.net/) | ≥ 0.18.4 | Mod loader |
| [Fabric API](https://modrinth.com/mod/fabric-api) | Per MC version (see table above) | Client tick events, keybindings, networking |
| Java | ≥ 21 | Runtime |

### Optional

| Dependency | Version | Purpose |
|------------|---------|---------|
| [Baritone](https://github.com/cabaletta/baritone) | Matching MC version | A\* pathfinding for navigation between build zones and supply chests. Without Baritone, the mod uses a built-in vanilla straight-line walker with auto-jump and stuck detection. |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `mods/` folder
3. Download the Smartmatica `.jar` for your MC version and place it in your `mods/` folder
4. *(Optional)* Install [Baritone](https://github.com/cabaletta/baritone/releases) (Fabric build) for proper pathfinding

## Usage

| Keybinding | Default | Action |
|------------|---------|--------|
| Toggle Printer | `Numpad 0` | Start/stop the printer |

### Commands

All commands use the `/printer` prefix:

- `/printer start`
  - Start printing
- `/printer stop`
  - Stop printing
- `/printer auto`
  - Toggle AutoBuild mode
- `/printer supply add`
  - Register the chest you're looking at as a supply chest
- `/printer supply list`
  - List all registered supply chests
- `/printer supply scan`
  - Show indexed supply inventory (item counts, shulker boxes found)
- `/printer supply clear`
  - Remove all supply chests
- `/printer status`
  - Show current printer status
- `/printer materials`
  - Bill of materials with supply inventory cross-reference

## Building from Source

### Prerequisites

- **JDK 21** or later

### Build Instructions

```bash
# Build all versions
./gradlew build

# Build a specific version
./gradlew :1.21.1:build
./gradlew :1.21.4:build
./gradlew :1.21.5:build
./gradlew :1.21.8:build
./gradlew :1.21.10:build
```

Build artifacts are placed in each version's `build/libs/` directory:
```
versions/1.21.1/build/libs/smartmatica-1.0.0+1.21.1.jar
versions/1.21.4/build/libs/smartmatica-1.0.0+1.21.4.jar
...
```

To collect all version JARs into one folder:
```bash
./gradlew buildAndCollect
# Output: build/libs/1.0.0/
```

### Build System

| Tool | Version | Purpose |
|------|---------|---------|
| [Stonecutter](https://stonecutter.kikugie.dev/) | 0.8.3 | Multi-version preprocessor — single source tree for all MC versions |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | 1.14-SNAPSHOT | Minecraft dev toolchain (decompilation, remapping, run configs) |
| [Gradle](https://gradle.org/) | 9.2.1 | Build automation |


```

### Cross-version Compatibility

The project uses Stonecutter comment predicates for version-specific code:

```java
/*? if >=1.21.10 {*//*
Vec3d playerPos = player.getSyncedPos();    // 1.21.10+ API
*//*?} else {*/
Vec3d playerPos = player.getPos();          // 1.21.1–1.21.8 API
/*?}*/
```

Key version boundaries handled:
- **≥ 1.21.5** — `PlayerInventory.selectedSlot` field access changes
- **≥ 1.21.8** — `PlayerInputC2SPacket` constructor changes
- **≥ 1.21.10** — `getSyncedPos()` replaces `getPos()`, `KeyBinding` changes

### Baritone Integration (Development)

Baritone is accessed **entirely via reflection** — there is no compile-time dependency on Baritone, and nothing is bundled into the output JAR. Users install the official [cabaletta/baritone](https://github.com/cabaletta/baritone) Fabric release separately.

At runtime, `PathWalker` detects Baritone via `Class.forName("baritone.api.BaritoneAPI")`. If found, it resolves all necessary API methods and constructors via `java.lang.reflect` and caches the handles in a static initializer. This means:

- **No external Maven repository** is needed in the build
- **No version coupling** — the reflection layer adapts to any Baritone release that exposes the standard `baritone.api` interfaces
- **Graceful fallback** — if Baritone is absent or the API is incompatible, the mod falls back to its built-in vanilla straight-line walker automatically

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
