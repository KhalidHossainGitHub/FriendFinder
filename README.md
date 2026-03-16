# FriendFinder

A Minecraft Fabric client mod for **1.21.1** that helps players navigate multiplayer worlds — find friends, mark locations, and coordinate with teammates without memorising coordinates.

Built with Fabric API, Java 21, and Gradle 8.8.

<p align="center">
  <img width="932" alt="FriendFinder Preview" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 1: FriendFinder In-Game Preview</b>
</p>

---

## Features

### 1. Circular HUD Minimap
- Rotating minimap pinned to the top-right corner of the HUD
- Height-shaded terrain with water depth coloring and ravine detection
- Compares block heights to neighbours (like vanilla cartography maps) so hills, cliffs, and ravines are clearly visible
- Shows nearby players (green), waypoints (teal), and pings (per-player color)
- Compass labels (N/S/E/W) rotate with the player's yaw
- Terrain cache that rebuilds only when the player moves blocks

<p align="center">
  <img width="932" alt="Minimap" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 2: Circular HUD Minimap</b>
</p>

### 2. Persistent World Map (M key)
- Full-screen map with a Minecraft filled-map-inspired parchment border
- **Persistent exploration** — terrain is recorded in the background as you walk. Pan back to areas you visited hours ago and the map is still there
- Data saved per server/world and per dimension as compressed binary files
- Height-shaded terrain, water depth, ravine-aware foliage piercing
- Scroll to zoom (1:1 to 1:16), zoom focuses toward cursor position
- Click-and-drag to pan, M or Escape to close
- Subtle coordinate grid that adapts to zoom level
- Bottom info bar: player position, cursor world-coordinates, zoom level
- Cardinal labels, crosshair, and control hints overlay
- Waypoints, players, and pings rendered on top of terrain

<p align="center">
  <img width="932" alt="World Map" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 3: Persistent World Map</b>
</p>

### 3. FriendFinder Menu (P key)
A single tabbed GUI opened with P. Click the tabs to switch between **Waypoints** and **Friends**.

<p align="center">
  <img width="932" alt="FriendFinder Menu" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 4: FriendFinder Menu — Waypoints Tab</b>
</p>

#### Waypoints Tab
- Type a name and click **"+ Add Here"** to save your current position (or press Enter)
- Scrollable list showing each waypoint's name, coordinates, and dimension
- **Teleport** and **Delete** buttons per entry
- Waypoints persist between sessions in `config/friendfinder/waypoints.json`
- Visible on the minimap, world map, and as 3D billboarded labels floating in the world
- Filtered by dimension (Overworld / Nether / End)
- Commands also available:
  ```
  /waypoint add <name>       Save current position
  /waypoint remove <name>    Delete a waypoint
  /waypoint list             Show all with distance
  /waypoint teleport <name>  TP to waypoint (requires server permission)
  ```

#### Friends Tab
- Lists all online players with their Minecraft skin head icons
- One-click **Teleport** buttons (sends `/tp <player>` to server)
- Scrollable for large player lists
- Shows player count

### 4. Friend Radar (always on)
- Indicators pinned to the **top edge** of the screen for every nearby player
- Position along the top reflects the player's relative direction — left of screen means the player is to your left, right means to your right
- Shows player name and distance in meters
- Always visible regardless of where you're looking
- Configurable range (default 200 blocks)

<p align="center">
  <img width="932" alt="Friend Radar" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 5: Friend Radar — Top-of-Screen Indicators</b>
</p>

### 5. Ping Beacon (G key)
- Press G to mark your current location for teammates
- **Beacon beam** — a translucent colored beam extends from the ping location to the sky, visible from far away
- **Per-player colors** — each player's pings have a unique, consistent color generated from their username
- Pings appear on the minimap, world map, as 3D beams in the world, and in chat
- Auto-fade after 30 seconds with a 5-second transparency ramp
- **Multiplayer sync** — when the mod is installed on the server, pings broadcast to all players with the mod. Falls back to local-only on vanilla servers
- Server-side rate limiting: 1 ping per 3 seconds per player

<p align="center">
  <img width="932" alt="Ping Beacon" src="public\friendfinder-preview.png">
  <br>
  <b>Figure 6: Ping Beacon with Per-Player Colors</b>
</p>

---

## Keybinds

| Key | Action |
|-----|--------|
| **M** | Open/close world map |
| **P** | Open/close FriendFinder menu (waypoints + friends) |
| **G** | Ping current location |

All keybinds are rebindable in Minecraft's Controls settings under the **FriendFinder** category.

---

## Configuration

Settings are stored in `.minecraft/config/friendfinder/config.json`:

```json
{
  "minimapSize": 120,
  "minimapMargin": 10,
  "radarRange": 200,
  "waypointVisible": true,
  "pingDurationSeconds": 30
}
```

---

## Technical Details & Challenges

### Terrain Rendering
The minimap and world map both use a **height-difference hillshading** algorithm inspired by vanilla Minecraft cartography. Each block's height is compared to its north and west neighbours — higher blocks are brightened, lower blocks are darkened. This creates the visual depth that makes ravines appear as dark gashes and mountains have bright ridgelines.

**Ravine detection** was a specific challenge: Minecraft's `MOTION_BLOCKING` heightmap can report a grass block at the top of a ravine edge, making it look like flat terrain. The solution is to peek below foliage blocks (grass, ferns) — if the block underneath is air, the renderer scans downward to find the actual solid surface and uses that block's color and height instead.

**Water depth** is handled by scanning downward from the surface through fluid blocks and darkening the blue proportionally (up to 24 blocks deep).

### Persistent Map Data
The world map uses a `MapDataManager` that runs in the background every tick, scanning up to 8 newly loaded chunks every 0.5 seconds using a spiral pattern outward from the player. Chunk color data is stored in a `HashMap<Long, int[]>` keyed by packed chunk coordinates.

Persistence uses **GZIP-compressed binary files** — one per dimension per server/world. Each chunk stores 256 ARGB color values (16x16 blocks). Data auto-saves every ~2 minutes and on disconnect/dimension change. A typical exploration session of 10,000 chunks uses about 2–4 MB on disk.

The world map screen itself does no sampling — it reads entirely from the `MapDataManager` cache, using a `lastCX/lastCZ` optimization to avoid redundant HashMap lookups for blocks in the same chunk.

**Trade-off vs. full-featured map mods:** Mods like Xaero's Minimap or JourneyMap achieve real-time dynamic updating through GPU texture atlases, multi-threaded sampling, block-change event hooks, and LOD systems — thousands of lines of rendering infrastructure. FriendFinder takes a simpler approach: snapshot chunks on first visit, persist them, and accept that terrain changes won't auto-reflect on already-cached areas. This keeps the codebase lightweight (~1,700 lines total) while still providing a fully functional persistent map.

### Networking
The ping system uses Fabric's `CustomPayload` API with a custom `PingPayload` record containing sender name and XYZ coordinates. Payloads are registered for both C2S (client-to-server) and S2C (server-to-client) channels.

The server-side handler (`FriendFinderInit`) relays incoming pings to all other connected players who have the mod. Rate limiting (3-second cooldown per player) prevents spam. On vanilla servers without the mod, `ClientPlayNetworking.canSend()` returns false and pings remain local-only — no crashes or errors.

The mod uses `environment: "*"` in `fabric.mod.json` with separate `main` and `client` entrypoints so the networking payload registration runs on both sides while all rendering code stays client-only.

### Rendering Architecture
- **HUD overlays** (minimap, radar) use `HudRenderCallback` with `DrawContext.fill()` for per-pixel terrain and marker drawing
- **3D world elements** (waypoint labels, ping beams) use `WorldRenderEvents.LAST` with `MatrixStack` transformations, `Tessellator`/`BufferBuilder` for custom geometry, and `TextRenderer.TextLayerType.SEE_THROUGH` for through-wall visibility
- **GUI screens** (world map, menu) extend `Screen` with custom `render()` overrides, `enableScissor` for clipping, and manual widget management for tabs and scrollable lists

### Per-Player Ping Colors
Player colors are generated deterministically from the username hash using **HSB color space** — the hash maps to a hue value while saturation (0.8) and brightness (1.0) are fixed. This guarantees every player gets a unique, bright, easily distinguishable color that's consistent across sessions.

---

## Building

**Requirements:** Java 21

```bash
# Windows PowerShell — set JAVA_HOME if Java 21 isn't default
$env:JAVA_HOME = "C:\path\to\jdk-21"

# Build
./gradlew build

# Output: build/libs/friendfinder-1.0.0.jar
```

### Development

```bash
./gradlew runClient
```

---

## Installation

### Client
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1
3. Place both `fabric-api-*.jar` and `friendfinder-1.0.0.jar` in `.minecraft/mods/`
4. Launch with the Fabric 1.21.1 profile

### Server (for ping sync)
1. Set your server to Fabric 1.21.1 (Aternos: Software → Fabric)
2. Install Fabric API on the server
3. Upload `friendfinder-1.0.0.jar` to the server's `mods/` folder
4. Restart the server

Without the mod on the server, everything works except pings won't sync between players.

---

## Project Structure

```
src/main/java/com/friendfinder/
├── FriendFinderMod.java              Client entry — keybinds, tick loop
├── FriendFinderInit.java             Shared entry — network payload registration
├── config/
│   └── FriendFinderConfig.java       JSON config (Gson)
├── menu/
│   └── FriendFinderMenuScreen.java   Tabbed GUI — waypoints + friends
├── minimap/
│   └── MinimapRenderer.java          Circular HUD minimap
├── worldmap/
│   ├── WorldMapScreen.java           Full-screen parchment-bordered map
│   └── MapDataManager.java           Persistent chunk exploration + background scanning
├── waypoint/
│   ├── Waypoint.java                 Data model
│   ├── WaypointManager.java          CRUD + JSON persistence
│   ├── WaypointCommand.java          /waypoint client commands
│   └── WaypointRenderer.java         3D billboarded labels
├── teleport/
│   └── TeleportMenuScreen.java       Legacy teleport screen (superseded by menu)
├── radar/
│   └── FriendRadarRenderer.java      Top-of-screen directional indicators
├── ping/
│   ├── PingManager.java              Ping lifecycle, per-player colors
│   └── PingBeamRenderer.java         3D beacon beams for pings
├── network/
│   ├── PingPayload.java              Custom network packet (C2S + S2C)
│   └── NetworkHandler.java           Fabric networking, server relay, rate limiting
└── util/
    └── PlayerTracker.java            Nearby player position provider

src/main/resources/
├── fabric.mod.json
└── assets/friendfinder/lang/en_us.json
```

---

## License

MIT
