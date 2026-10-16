# Fractured Reflections

A **1 vs 4 asymmetrical horror** mod for **Minecraft 1.20.1 (Fabric)**. A procedurally generated gothic
mansion exists twice: once in the **Main World**, and once rebuilt in blood-red and black inside the
**Mirror Dimension**. One player is **The Fractured** — a gaunt figure of floating glass shards that
moves through mirrors — and up to four **Survivors** must complete three glass rituals to banish it.

> **Build status:** the full source is here, but it has **not been compiled yet**. The environment it was
> written in could not reach the Fabric, Mojang or Modrinth Maven repositories. A local `javac` pass
> found no syntax errors and no broken references between the mod's own classes, `build.gradle` parses,
> and the refraction puzzle was simulated as solvable in all four orientations. Expect a short round of
> mapping/API fixes on the first build (see [Verification notes](#verification-notes)).

---

## 1. Build the jar (one click)

You need a **JDK 17 or 21** ([Eclipse Temurin](https://adoptium.net) works). Nothing else: the Gradle
wrapper downloads Gradle, Minecraft and every dependency on the first run (a few minutes).

| System | Do this | Result |
|---|---|---|
| Windows | double-click **`build.bat`** | `dist\fractured-reflections-0.1.0.jar` |
| macOS / Linux | run **`./build.sh`** | `dist/fractured-reflections-0.1.0.jar` |
| Any | `./gradlew dist` | same |

Put the jar in `.minecraft/mods` together with **Fabric API** for 1.20.1. **Immersive Portals**,
**LambDynamicLights** and **Simple Voice Chat** are recommended; everything still works without them.

More scripts live in `scripts/`:

| Script (`.bat` and `.sh`) | What it does |
|---|---|
| `run-client` | Dev client "Survivor" |
| `run-client2` | Second dev client "Fractured", for 1v1 tests |
| `run-server` | Dev server (needs `accept_minecraft_eula=true` in `gradle.properties`) |
| `gen-sources` | Decompiles Minecraft so IntelliJ can show and navigate its sources |

## 2. Run it in IntelliJ IDEA (with Immersive Portals)

1. **File ▸ Open…**, select the project folder (or its `build.gradle`), choose **Open as Project** and trust it.
2. **File ▸ Project Structure ▸ Project**: set the SDK to a JDK 17 or 21.
3. Wait for the Gradle sync to finish. It downloads Minecraft, Fabric, **Immersive Portals**, Cloth Config,
   LambDynamicLights, Simple Voice Chat and Mod Menu. All of them are wired into every dev run through
   `modLocalRuntime`.
4. The run configurations are generated automatically:
   * **Fractured Reflections - Client 1 (Survivor)**
   * **Fractured Reflections - Client 2 (Fractured)**
   * **Fractured Reflections - Dev Server**

   If they don't appear, open the Gradle tool window and click **Reload All Gradle Projects**.
5. Optional: run **`scripts/gen-sources`** once, so <kbd>Ctrl</kbd>-click on a Minecraft class opens readable source.
6. Debugging: start a configuration with **Debug**, edit code, then **Build ▸ Recompile** (<kbd>Ctrl+Shift+F9</kbd>)
   to hot-swap method bodies without restarting.

To build the release jar from inside IntelliJ, open the Gradle tool window and run
**Tasks ▸ build ▸ dist**.

Immersive Portals can be turned off for faster dev launches with `use_immersive_portals=false` in
`gradle.properties`. The mod then falls back to its own contact-teleport portals.

### Testing a match with two clients

* **LAN (quickest):** start *Client 1*, create a creative world with cheats on, then
  *Open to LAN* (cheats on). Start *Client 2* and join from the multiplayer list. In Client 1, run `/fr start Fractured`.
* **Dev server:** read the [Minecraft EULA](https://aka.ms/MinecraftEULA) and set `accept_minecraft_eula=true`
  in `gradle.properties`. Then start *Dev Server* and both clients, and join `localhost`. The build pre-writes an
  offline-mode `server.properties` and makes both dev names operators.
* **Solo:** set `"minPlayers": 1` in `run/config/fractured_reflections.json` and play as a lone survivor.

| Command | Effect |
|---|---|
| `/fr start [player]` | Start with every non-spectator online. The Fractured is random, or the named player. |
| `/fr stop` | Abort and return everyone to the overworld spawn. |
| `/fr status` | Phase, timer, roles, stamina, sanity. |
| `/fr sanity <players> <value>` | Debug: set sanity (try 15 to see hallucinations, Glass Sense and screen cracks). |

### Controls (rebindable under *Controls ▸ Fractured Reflections*)

| Key | Who | Action |
|---|---|---|
| right-click flashlight / `L` | Survivor | Toggle the flashlight |
| right-click wardrobe | Survivor | Hide inside (sneak to leave) |
| `R` | Fractured | **Portal Transformation**: look at a mirror |
| `G` | Fractured | **Dimension Drag**: grab the survivor in front of you |
| `V` | Fractured | **Shapeshift**: copy the survivor you look at (press again to drop it) |
| `Z` | Fractured | **Glass Echo**: release a sprinting decoy |
| `B` | Fractured | **Shatter Blackout**: break every light within 30 blocks |
| right-click wardrobe | Fractured | Search it and tear out whoever hides inside |
| walk or fly into a mirror | Fractured | **Entity Phasing** into the twin world (sneak to stay put) |

---

## 3. Gameplay

1. **Generating.** A new mansion is built into both dimensions: 3 floors of 5×5 rooms with maze-like
   doors, archways and stairwells. There are 10 room types: library, crypts, banquet hall, observatory,
   conservatory, chapel, ballroom, bedchamber, gallery and foyer.
2. **Preparing (15 s).** Survivors spawn in the Foyer with a flashlight and a battery. The Fractured
   spawns in the farthest crypt, inside the Mirror Dimension.
3. **The Hunt (15 min).** Each of the three Glass Ritual Altars needs three steps, in order:
   1. **Refraction Puzzle.** Rotate the lenses until every Lumen Prism laser lands on a focal point.
   2. **Shard Forging.** Unlock sealed chests: each code is on a sign in the same room, written backwards
      (inside the Mirror Dimension it reads the right way round). Smelt the Cursed Glass Fragments in a
      furnace, fuse 3 Molten Mirror Glass into a **Pure Mirror** in your 2×2 grid, then seat it in the altar.
   3. **The Cleansing Stand.** Keep at least one survivor in the ring for 60 s. It tolls through the whole
      mansion, everyone inside glows through walls, and The Fractured's HUD points at it.
4. **End.** Survivors win when all altars are cleansed (*Banishment*). The Fractured wins when every
   survivor is gone (killed, or sanity reached zero) or the timer runs out.

**Survivor tools**
* **Wardrobes** (two in every bedchamber, sometimes elsewhere): hide unseen and slowly recover sanity, for
  up to 40 s. If The Fractured stands close it hears your heartbeat. It can search wardrobes: an occupied
  one is torn open, and you are stunned and lose sanity.
* **Laudanum Tonic**, found in sealed chests, restores 35 sanity.

**Glass Sense.** Survivors below 25 % sanity glow blood-red through walls, but only to The Fractured.

**Trapped in the Mirror Dimension**
* Sanity drains at **double speed**.
* You see spectral **Ghost Echoes** of teammates still in the Main World.
* A **Glowing Blue Cracked Mirror**, marked by a pale blue light column, spawns far away. Jump through it to escape.
* Your voice leaks faintly into the Main World.

---

## 4. The Mirror Dimension: blood and soot

* **Rebuilt materials.** A two-way block palette (`world/MirrorPalette`) builds the mirror mansion as
  dark oak → crimson, deepslate → nether brick, glass → red/black stained glass, moss → crimson nylium,
  torches → redstone torches, skulls → wither skulls, and so on. Live sync translates every change in
  both directions, so a door opened in the Main World opens its crimson twin.
* **Custom shader chain** `shaders/post/mirror_realm.json`, 7 passes:
  1. `bright`: extracts light sources and anything already red.
  2. `bloom_blur`: separable 9-tap gaussian, run 4 times (tight, then wide).
  3. `mirror_grade` (below), then a final `blit` back to the screen.
  
  The grade is a filmic crimson ramp. Blacks crush to true black, mids sink to deep blood red, and
  highlights burn through crimson to a sickly rose. Blood-red stays saturated, and the red bloom makes
  every lantern bleed light. It also adds a breathing heat-shimmer, film grain, heavy vignette, heartbeat
  edge flashes, and **signal tears** that get more frequent as the killer approaches.
* **Atmosphere.** Blood-black fog, glass dust that smoulders as red embers, and muffled audio (OpenAL
  low-pass).
* A second, light chain (`fractured.json`) runs everywhere. It handles the sanity warp, chromatic split,
  the **shattered-glass refraction overlay** (Voronoi shards that each bend the image and glow along their
  cracks), the flashlight cone and flicker, and one-shot flashes. Both chains are skipped when idle.

## 5. The Fractured

`client/render/FracturedShardRenderer` + `FracturedPose`:

* **Body.** A two-metre, hunched skeleton of more than 100 faceted **octahedral glass crystals** on a 13-bone
  rig: crowned skull, splayed ribcage, spine, and arms long enough to hang past the knees, each ending in
  a fan of needle claws.
* **Lighting.** Vanilla entity lighting shades every facet. A view-dependent fresnel rim bleeds red at
  grazing angles, and facets that catch the light flare with an emissive glint, so it sparkles even in
  total darkness.
* **Details.**
  * A red **heart** of nested, counter-rotating crystals beats in the ribcage.
  * **Lightning veins** crackle from the heart to the limbs.
  * Two burning **eye slits** flare with every head twitch.
  * A **halo** of shards orbits the shoulders.
* **Animation layers.** Breathing idle, walk and sprint cycles (knees bend, arms counter-swing, the body
  leans into speed), crouch, a dangling flight pose, an overhead **claw slash** when attacking, and
  sudden **head twitches**.
* **Mirror Crawl.** After crossing a mirror, each shard flies in from the glass on its own staggered
  delay while the body unfolds from a crawl. Glass splinters erupt from the mirror and nearby cameras
  shake.
* **Also:** first-person shard claws, a trail of splinters and sparks when moving fast, and a dim pulsing
  dynamic light (LambDynamicLights) that gives it away in dark corridors.

## 6. VFX

| Effect | Where |
|---|---|
| Refraction **lasers**: white-hot core in a cyan glow, starburst on the focal points, traced live client-side | `RitualAltarRenderer`, `block/BeamTracer` |
| Cleansing **ring** with a filling progress arc and orbiting runes; a **light column** that grows into a towering pillar when cleansed | `RitualAltarRenderer` |
| Escape mirror **vortex** (end-gateway shader plus spinning stars) and a pale blue beacon | `EscapeMirrorRenderer` |
| Mirror surfaces: silvered glass with the enchantment-glint sheen; transformed mirrors show the end-portal rift | `MirrorBlockEntityRenderer` |
| **Camera shake**: blackouts, portals tearing open, drags, nearby mirror crawls | `mixin/client/CameraShakeMixin` |
| **Screen cracks**: dragged, shattered, escaping, low sanity, ambient in the mirror world | `composite.fsh` |
| Dimension Drag **tendrils**, portal **shockwaves**, blackout **shard bursts**, elimination and banishment explosions | `FracturedAbilities`, `PortalManager`, `BlackoutManager`, `Match` |
| Peeking through the **wardrobe slats** while hiding | `client/hud/FRHud` |

---

## 7. Architecture map

| Feature | Where |
|---|---|
| Entrypoint, events, commands | `FracturedReflections`, `game/MatchManager`, `game/MatchCommands` |
| Match state machine, win conditions, client sync | `game/Match` |
| Config | `FRConfig` → `config/fractured_reflections.json` |
| Mansion generation, room modules, tick-sliced builder | `world/mansion/*` |
| Real-time sync and mirror palette | `world/MirrorSync`, `world/MirrorPalette`, `mixin/WorldMixin` |
| Mirrors, phasing, portals, escape mirrors | `block/MirrorBlock`, `game/MirrorTravel`, `game/PortalManager`, `game/EscapeMirrors` |
| The Fractured's abilities | `game/FracturedAbilities`, `entity/GlassEchoEntity`, `game/BlackoutManager` |
| Stamina, sanity, hiding | `game/SurvivorSystems`, `game/HidingManager`, `block/WardrobeBlock` |
| Rituals | `block/RitualAltarBlockEntity`, `block/BeamTracer`, lens/prism/focal blocks, puzzle chests |
| Items | `item/FlashlightItem`, `BatteryItem`, `SanityTonicItem` |
| Networking (13 channels) | `network/*`, `client/ClientNetworking` |
| HUD | `client/hud/FRHud` |
| Hallucinations, Ghost Echoes, disguises | `client/HallucinationController`, `GhostEchoManager`, `DisguiseHelper` + mixins |
| Rendering and shaders | `client/render/*`, `assets/.../shaders/*` |
| Integrations | `compat/ImmersivePortalsCompat` (reflective), `compat/voicechat`, `compat/lambdynlights` |

## Premade assets only

No textures, sounds or models were authored for this mod. Every sound event in `sounds.json` points at
a vanilla sound event, and every model references vanilla textures (glass, ice, crimson, gold, enchanting
table, door, spyglass, dragon's breath...). To swap in a licensed SFX pack, point a `sounds.json` entry at
`fractured_reflections:<file>` and drop the `.ogg` into `assets/fractured_reflections/sounds/`. The only
authored rendering code is the GLSL in `shaders/program/`.

## Integrations

| Mod | Used for | Required? |
|---|---|---|
| Fabric API | everything | yes |
| Immersive Portals | seamless mirror portals and optional true reflections, bound reflectively | recommended |
| LambDynamicLights | flashlight light and flicker, the Fractured's pulsing glow | recommended |
| Simple Voice Chat | voice identity, killer silence, mirror voice leak | recommended |

## Verification notes

These parts were written against my knowledge of the 1.20.1 Yarn mappings and the integration APIs, and
have not been compiled. Check them first if the build complains:

* Mixin targets:
  * `GameRenderer#render` (injected after `WorldRenderer#drawEntityOutlinesFramebuffer`)
  * `GameRenderer#tiltViewWhenHurt`
  * `JsonEffectShaderProgram#<init>/loadEffect`
  * `PostEffectProcessor.passes`
  * `Source#play` / `Source.pointer`
  * `ClientPlayerEntity#canSprint`
  * `PlayerEntityRenderer#renderRightArm/renderLeftArm`
  * `MinecraftClient#hasOutline`
  * `Entity#getTeamColorValue`
* APIs:
  * `voicechat-api` 2.5.0: `toLocationalSoundPacketBuilder`, `sendLocationalSoundPacketTo`
  * LambDynamicLights 2.3.x: `DynamicLightsInitializer`, `DynamicLightHandlers`
* Maven version strings in `gradle.properties`: Modrinth releases of Immersive Portals, Cloth Config,
  Mod Menu, LambDynamicLights and Simple Voice Chat.

`JsonEffectShaderProgramMixin` uses `require = 0`, and the post chains catch load failures. If anything
there moves, the effect is disabled and the game keeps running.
