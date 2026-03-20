# Minecraft Orchestrated Automation & Response

A standalone Fabric client mod for automated block placement, spawn-proofing, and container indexing. Built for everyone.

> **Early Version Advisory** — It works, but expect rough edges. File an issue for bugs or open a PR to improve things.

## Featurets

### Schematic Printer
- **Manual mode** — toggle to place blocks from a loaded `.litematic` within reach
- **AutoBuild mode** — fully automated building with zone-based progression
- **Baritone pathfinding** — A\* navigation via [Baritone](https://github.com/cabaletta/baritone); falls back to a built-in vanilla walker when absent
- **Supply chest restocking** — walks to configured chests to grab materials mid-build; indexes contents including shulker boxes
- **Shulker box unloading** — places, opens, loots, and breaks shulkers from supply chests; builds and cleans up temporary platforms
- **Scaffold cleanup** — tracks blocks placed by Baritone (bridges, pillars) and removes them after the build
- **Self-correction** — detects misplaced/wrong blocks and breaks + replaces them
- **Smart tool selection** — picks the fastest tool from inventory for breaking
- **Block property handling** — stairs, slabs, trapdoors, doors, logs, pillars, and other directional blocks
- **Liquid pass** — places water/lava after all solid blocks to avoid breaking pathing
- **Build checkpoints** — persists progress to disk for resume after disconnect

### SpawnProofer
- **Automated light placement** — scans a region for dark spawnable surfaces and places light sources
- **Embed mode** — replaces ground blocks with full-block light sources (glowstone, sea lanterns, froglights, etc.)
- **Greedy solver** — plans optimal light positions to cover the most dark spots per placement
- **Supply chest support** — restocks light source materials automatically

### Stash Scanner
- **Container indexing** — scans chests, barrels, shulker boxes, and hoppers in a defined region
- **Incremental waypoints** — walks between zones for areas beyond render distance
- **CSV export** — exports full inventory index to file

## Supported Versions

| Minecraft | Fabric Loader | Fabric API | Java |
|-----------|---------------|------------|------|
| 1.21–1.21.1 | ≥ 0.18.4 | 0.116.9+1.21.1 | 21 |
| 1.21.4 | ≥ 0.18.4 | 0.119.4+1.21.4 | 21 |
| 1.21.5 | ≥ 0.18.4 | 0.128.2+1.21.5 | 21 |
| 1.21.8 | ≥ 0.18.4 | 0.136.1+1.21.8 | 21 |
| 1.21.9–1.21.10 | ≥ 0.18.4 | 0.138.4+1.21.10 | 21 |
| 1.21.11 | ≥ 0.18.4 | 0.141.3+1.21.11 | 21 |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (≥ 0.18.4)
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`
3. Drop the MOAR `.jar` for your MC version into `mods/`
4. *(Optional)* Install [Baritone](https://github.com/cabaletta/baritone/releases) for pathfinding

## Usage

### Keybinding

| Key | Default | Action |
|-----|---------|--------|
| Toggle Printer | `Numpad 0` | Start/stop the printer |

### Printer Commands

| Command | Description |
|---------|-------------|
| `/printer toggle` | Toggle the printer on/off |
| `/printer autobuild` | Toggle AutoBuild mode (automated zone progression) |
| `/printer load <file>` | Load a `.litematic` schematic at current position |
| `/printer unload` | Unload the current schematic |
| `/printer detect` | Auto-detect active Litematica placements |
| `/printer list` | List available `.litematic` files |
| `/printer here` | Re-anchor schematic at current position |
| `/printer pos <x> <y> <z>` | Set anchor to specific coordinates |
| `/printer status` | Show progress, anchor, completion percentage |
| `/printer materials` | Bill of materials with supply cross-reference |
| `/printer resume` | Resume from last saved checkpoint |
| `/printer speed [1–20]` | Set or show placement speed (blocks/sec, default 13) |
| `/printer sort [mode]` | Set build order: `bottom_up` (default), `top_down`, `nearest` |
| `/printer air` | Toggle air placement (floating blocks) |
| `/printer supply add [x y z]` | Mark a chest/barrel/shulker as supply source |
| `/printer supply remove` | Unmark a supply chest |
| `/printer supply list` | List all supply chests |
| `/printer supply scan` | Show indexed supply inventory summary |
| `/printer supply clear` | Clear all supply chests |

### SpawnProof Commands

| Command | Description |
|---------|-------------|
| `/spawnproof pos1 [x y z]` | Set corner 1 (default: player position) |
| `/spawnproof pos2 [x y z]` | Set corner 2 |
| `/spawnproof start` | Begin placing lights in the defined region |
| `/spawnproof stop` | Stop and reset |
| `/spawnproof pause` | Pause mid-operation |
| `/spawnproof resume` | Resume from pause |
| `/spawnproof scan` | Scan for dark spots without placing |
| `/spawnproof status` | Show state, corners, dark spots, lights placed |
| `/spawnproof lightsrc [block]` | Set or show light source (torch, glowstone, lantern, etc.) |
| `/spawnproof embed` | Toggle embed mode (replace ground with light source) |
| `/spawnproof supply add` | Mark a chest as light source supply |
| `/spawnproof supply remove` | Unmark a supply chest |
| `/spawnproof supply list` | List supply chests |

### Stash Commands

| Command | Description |
|---------|-------------|
| `/stash pos1 [x y z]` | Set corner 1 (default: player position) |
| `/stash pos2 [x y z]` | Set corner 2 |
| `/stash scan` | Scan all containers in the region |
| `/stash stop` | Abort scanning |
| `/stash status` | Show scan state and index summary |
| `/stash export` | Export inventory index to CSV |
| `/stash clear` | Clear the index |

## Building from Source

Requires **JDK 21+**.

```bash
# Build all versions
./gradlew build

# Build a specific version
./gradlew :1.21.8:build

# Collect all JARs into one folder
./gradlew buildAndCollect
# Output: build/libs/1.1.0/
```

Build artifacts go to `versions/<mc>/build/libs/moar-1.1.0+<mc>.jar`.

### Build System

| Tool | Version | Purpose |
|------|---------|---------|
| [Stonecutter](https://stonecutter.kikugie.dev/) | 0.8.3 | Multi-version preprocessor |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | 1.14-SNAPSHOT | Minecraft dev toolchain |
| [Gradle](https://gradle.org/) | 9.2.1 | Build automation |

### Baritone Integration

Baritone is accessed entirely via reflection — no compile-time dependency, nothing bundled. `PathWalker` detects Baritone at runtime via `Class.forName` and caches method handles. If absent, the mod falls back to its vanilla walker automatically.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

**If you fork:** AGPL v3 requires forks to be public with changes indicated. You cannot obfuscate the code or restrict access. If you find a fork violating this, please let us know.
