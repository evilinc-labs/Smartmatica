# MOAR — Minecraft Orchestrated Automation & Response

A client-side Fabric mod that automates schematic building, spawn-proofing, and container management. Load a `.litematic`, point it at some supply chests, and let it build.

## What It Does

### Schematic Printer
Load a Litematica schematic and build it automatically — or toggle manual mode to place blocks yourself.

- **AutoBuild** — walks to each zone, grabs materials from supply chests, places blocks, and moves on
- **Supply restocking** — indexes chests and shulker boxes; walks back to resupply when inventory runs low
- **Shulker handling** — places, opens, loots, and breaks shulkers from supply; cleans up temp platforms
- **Scaffold cleanup** — removes bridges and pillars placed during pathfinding after the build
- **Self-correction** — detects wrong blocks and replaces them
- **Smart tool selection** — picks the fastest tool for breaking
- **Directional blocks** — handles stairs, slabs, trapdoors, doors, logs, pillars, etc.
- **Liquid pass** — places water/lava after solids to keep pathfinding intact
- **Checkpoints** — saves progress to SQLite; resume after disconnect with `/printer resume`
- **Area clearing** — mines out a region before building; dumps items into designated chests
- **Pathfinding** — uses [Baritone](https://github.com/cabaletta/baritone) if installed, otherwise falls back to a built-in vanilla walker

### SpawnProofer
Define a region and let it place light sources on every dark spawnable surface.

- **Greedy solver** — picks positions that cover the most dark spots per placement
- **Embed mode** — swaps ground blocks for full-block lights (glowstone, sea lanterns, froglights)
- **Supply chests** — restocks light sources automatically

### Stash Manager
Scan, index, and organize containers across large areas.

- **Container indexing** — scans chests, barrels, shulker boxes, and hoppers in a defined region
- **Auto-organizing** — sorts items into columns, packs shulkers, handles overflow
- **Waypoint walking** — navigates between zones for areas beyond render distance
- **CSV export** — exports the full inventory index to file
- **Dump chests** — designate chests for depositing mined items during area clearing
- **REST API** — embedded HTTP server with Prometheus metrics for Grafana dashboards
- **Webhook** — POST JSON to n8n or other services on scan completion

## Supported Versions

| Minecraft | Fabric API | Java |
|-----------|------------|------|
| 1.21–1.21.1 | 0.116.9+1.21.1 | 21 |
| 1.21.4 | 0.119.4+1.21.4 | 21 |
| 1.21.5 | 0.128.2+1.21.5 | 21 |
| 1.21.8 | 0.136.1+1.21.8 | 21 |
| 1.21.9–1.21.10 | 0.138.4+1.21.10 | 21 |
| 1.21.11 | 0.141.3+1.21.11 | 21 |

All versions require [Fabric Loader](https://fabricmc.net/use/) ≥ 0.18.4.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) ≥ 0.18.4
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`
3. Drop the MOAR `.jar` for your MC version into `mods/`
4. *(Optional)* Install [Baritone](https://github.com/cabaletta/baritone/releases) for pathfinding

## Quick Start

1. Load a schematic: `/printer load mybase.litematic`
2. Stand where the build origin should be: `/printer here`
3. Mark your supply chests: `/printer supply add` (look at each chest)
4. Start building: `/printer autobuild`
5. Check progress: `/printer status`

## Commands

### Printer

| Command | What it does |
|---------|--------------|
| `/printer toggle` | Start/stop the printer |
| `/printer autobuild` | Toggle fully automated building |
| `/printer load <file>` | Load a `.litematic` schematic |
| `/printer unload` | Unload the current schematic |
| `/printer detect` | Auto-detect active Litematica placements |
| `/printer list` | List available schematic files |
| `/printer here` | Anchor schematic to current position |
| `/printer pos <x> <y> <z>` | Anchor schematic to specific coordinates |
| `/printer status` | Show progress and completion percentage |
| `/printer materials` | Show required materials vs. supply inventory |
| `/printer resume` | Resume from last checkpoint |
| `/printer speed [1–20]` | Get/set placement speed (default: 13 blocks/sec) |
| `/printer sort [mode]` | Set build order: `bottom_up`, `top_down`, `nearest` |
| `/printer air` | Toggle air placement (floating blocks) |
| `/printer supply add [x y z]` | Mark a container as a supply source |
| `/printer supply remove` | Unmark the nearest supply chest |
| `/printer supply list` | List all supply chests |
| `/printer supply scan` | Show indexed supply inventory summary |
| `/printer supply clear` | Remove all supply chests |
| `/printer dump add [x y z]` | Mark a container as a dump chest |
| `/printer dump remove` | Unmark the nearest dump chest |
| `/printer dump list` | List all dump chests |
| `/printer dump clear` | Remove all dump chests |

### SpawnProofer

| Command | What it does |
|---------|--------------|
| `/spawnproof pos1 [x y z]` | Set corner 1 (default: player position) |
| `/spawnproof pos2 [x y z]` | Set corner 2 |
| `/spawnproof start` | Begin placing lights |
| `/spawnproof stop` | Stop and reset |
| `/spawnproof pause` | Pause mid-run |
| `/spawnproof resume` | Resume from pause |
| `/spawnproof scan` | Count dark spots without placing |
| `/spawnproof status` | Show state, region, and placement count |
| `/spawnproof lightsrc [block]` | Get/set light source block |
| `/spawnproof embed` | Toggle embed mode |
| `/spawnproof supply add` | Mark a chest as light supply |
| `/spawnproof supply remove` | Unmark a supply chest |
| `/spawnproof supply list` | List supply chests |

### Stash

| Command | What it does |
|---------|--------------|
| `/stash pos1 [x y z]` | Set corner 1 (default: player position) |
| `/stash pos2 [x y z]` | Set corner 2 |
| `/stash scan` | Scan all containers in the region |
| `/stash organize` | Auto-organize: column sort, shulker pack, overflow |
| `/stash organize stop` | Stop organizing |
| `/stash stop` | Stop scanning |
| `/stash status` | Show scan state and index summary |
| `/stash export` | Export inventory index to CSV |
| `/stash clear` | Clear the index |
| `/stash dump add [x y z]` | Mark a dump chest |
| `/stash dump remove` | Unmark the nearest dump chest |
| `/stash dump list` | List all dump chests |
| `/stash dump clear` | Remove all dump chests |

### API & Monitoring

MOAR includes an embedded HTTP API for Grafana dashboards, Prometheus scraping, and n8n webhook integrations. All settings live in `config/moar/moar.properties` (auto-created on first launch).

#### Configuration (`config/moar/moar.properties`)

```properties
# Enable the embedded API server
api.enabled=false
# Bind address (use 0.0.0.0 to expose externally)
api.bind=127.0.0.1
# HTTP port
api.port=8585
# Bearer token for auth (leave blank to disable auth)
api.key=
# POST JSON to this URL on scan completion (n8n, etc.)
webhook.url=
```

#### REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/status` | GET | Scanner state, index size, region info |
| `/api/v1/containers?page=1&size=50` | GET | Paginated container list |
| `/api/v1/search?item=diamond` | GET | Find containers holding an item |
| `/api/v1/stats` | GET | Aggregate statistics (JSON) |
| `/api/v1/metrics` | GET | Prometheus-format metrics |
| `/api/v1/organizer` | GET | Organizer state and progress |
| `/api/v1/webhook/test` | POST | Webhook connectivity test |

All endpoints accept `Authorization: Bearer <api.key>` when `api.key` is set.

#### Grafana Setup

See [docs/grafana-setup.md](docs/grafana-setup.md) for a step-by-step guide to connect Prometheus + Grafana dashboards via Docker Compose.

### Keybinds

| Key | Action |
|-----|--------|
| `Numpad 0` | Toggle printer on/off |

## Building from Source

Requires **JDK 21+**.

```bash
./gradlew build                  # Build all versions
./gradlew :1.21.8:build          # Build one version
./gradlew buildAndCollect        # Collect all JARs → build/libs/1.2.1/
```

Output: `versions/<mc>/build/libs/moar-1.2.1+<mc>.jar`

### Build Stack

| Tool | Version |
|------|---------|
| [Stonecutter](https://stonecutter.kikugie.dev/) | 0.8.3 |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | 1.16.1 |
| [Gradle](https://gradle.org/) | 9.4.0 |

### Baritone Integration

Baritone is loaded via reflection at runtime — no compile dependency, nothing bundled. If Baritone isn't installed, the mod uses its built-in vanilla walker instead.

## License

[GNU Affero General Public License v3.0](LICENSE)

Forks must remain public with changes indicated. See the license for details.
