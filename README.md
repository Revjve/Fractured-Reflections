# Fractured Reflections

A **1 vs 4 asymmetrical horror** mod for **Minecraft 1.20.1 (Fabric)**. A procedurally generated gothic
mansion exists simultaneously in the **Main World** and an inverted **Mirror Dimension**. One player is
**The Fractured** — a killer made of floating glass shards who moves through mirrors — and up to four
**Survivors** must complete three glass rituals to banish it.

> **Build status:** the full source is here, but it has **not been compiled yet**. The environment it was
> written in could not reach the Fabric, Mojang or Modrinth Maven repositories. A local `javac` pass
> found no syntax errors, and the refraction puzzle was simulated as solvable in all four orientations.
> Expect a short round of mapping/API fixes on the first `./gradlew build` (see
> [Verification notes](#verification-notes)).

---

## Quick start

```bash
./gradlew runClient                    # dev client "Survivor"
./gradlew runClient2                   # second dev client "Fractured" (for 1v1 testing)
./gradlew build                        # -> build/libs/fractured-reflections-0.1.0.jar
```

You need Java 17+ (the wrapper downloads Gradle 8.8). In game, an operator runs:

| Command | Effect |
|---|---|
| `/fr start [player]` | Start a match with every non-spectator online. The Fractured is random, or the named player. |
| `/fr stop` | Abort and return everyone to the overworld spawn. |
| `/fr status` | Show the phase, timer, roles, stamina and sanity. |
| `/fr sanity <players> <value>` | Debug: set sanity, e.g. to try out hallucinations. |

Set `"minPlayers": 1` in `config/fractured_reflections.json` to test alone. You play as a survivor with no killer.

### Controls (rebindable under *Controls → Fractured Reflections*)

| Key | Who | Action |
|---|---|---|
| Right-click flashlight / `L` | Survivor | Toggle the flashlight |
| `R` | Fractured | **Portal Transformation**: look at a mirror |
| `G` | Fractured | **Dimension Drag**: grab the survivor in front of you |
| `V` | Fractured | **Shapeshift**: copy the survivor you look at (press again to drop it) |
| `Z` | Fractured | **Glass Echo**: release a sprinting decoy |
| `B` | Fractured | **Shatter Blackout**: break every light within 30 blocks |
| walk or fly into a mirror | Fractured | **Entity Phasing** into the twin world (sneak to stay put) |

---

## Gameplay loop

1. **Generating.** The mansion is built into both dimensions over several ticks, with chunks force-loaded.
2. **Preparing (15 s).** Survivors spawn in the Foyer of the Main World with a flashlight and a battery.
   The Fractured spawns in the farthest crypt of the **Mirror Dimension**.
3. **The Hunt (15 min).** Each of the three Glass Ritual Altars needs three steps, in order:
   1. **Refraction Puzzle.** Rotate the lenses (use = clockwise, sneak-use = counter-clockwise). Each Lumen Prism beam must land on a focal point beside the altar.
   2. **Shard Forging.** Open the sealed chests with glyph puzzles; the code is on a sign in the same room, written backwards. Collect the Cursed Glass Fragments, melt them in a mansion furnace into Molten Mirror Glass, and fuse 3 of those into a **Pure Mirror** in the 2×2 inventory grid. Then seat it in the altar.
   3. **The Cleansing Stand.** Start the ring and keep at least one survivor inside it for 60 s. Every 2 s it tolls loudly. Everyone inside it glows through walls, and The Fractured's HUD points to it.
4. **End.**
   * Survivors win when all altars are cleansed: *Banishment*.
   * The Fractured wins when every survivor is gone (killed, or sanity drained to zero) or the timer runs out.

The **Mirror Dimension** works like this:
* The Fractured can drag survivors into it.
* Survivors' sanity drains at **double speed** there.
* They see spectral **Ghost Echoes** of their teammates still in the Main World.
* A **Glowing Blue Cracked Mirror** spawns far away from each trapped survivor; they must **jump through it** to escape.
* Signs there show puzzle codes the right way round.

---

## Architecture map

| Feature | Where |
|---|---|
| Mod entrypoint, events, commands | `FracturedReflections`, `game/MatchManager`, `game/MatchCommands` |
| Match state machine, win conditions, client sync | `game/Match` |
| Tunable values | `FRConfig` → `config/fractured_reflections.json` |
| **Mansion generation**: grid, maze, floors, stairwells, room modules | `world/mansion/MansionGenerator`, `RoomDecorators`, `RoomContext` |
| Tick-sliced builder for both worlds | `world/mansion/BuildJob`, `BuildPlan` |
| Arena dimensions (void, flat) | `data/fractured_reflections/dimension*/` |
| **Real-time block sync** between the two worlds | `world/MirrorSync` + `mixin/WorldMixin` (hooks `World#setBlockState`) |
| Mirror blocks, phasing, portal fallback | `block/MirrorBlock`, `game/MirrorTravel` |
| Portal Transformation (timed) | `game/PortalManager`, `compat/ImmersivePortalsCompat` |
| Dimension Drag, Shapeshift, Glass Echo, Blackout | `game/FracturedAbilities`, `entity/GlassEchoEntity`, `game/BlackoutManager` |
| Escape mirrors | `block/EscapeMirrorBlock`, `game/EscapeMirrors` |
| Stamina and sanity simulation | `game/SurvivorSystems` (server), `mixin/client/ClientPlayerEntityMixin` (sprint gate) |
| Rituals: altar state machine, beam tracing | `block/RitualAltarBlockEntity`, `LensBlock`, `BeamEmitterBlock`, `FocalPointBlock` |
| Puzzle chests | `block/PuzzleChestBlock(Entity)`, `client/screen/PuzzleChestScreen` |
| Flashlight and batteries | `item/FlashlightItem`, `item/BatteryItem`, `compat/lambdynlights/FRDynamicLights` |
| Network packets (13 channels) | `network/FRPackets`, `ServerNetworking`, `client/ClientNetworking` |
| HUD: stamina, sanity, task list, ability cooldowns, altar pings | `client/hud/FRHud` |
| Hallucinations and heartbeat | `client/HallucinationController` |
| Ghost Echoes | `client/GhostEchoManager`, `GhostEchoPlayer` |
| Shard body and Mirror Crawl animation | `client/render/FracturedShardRenderer`, `mixin/client/PlayerEntityRendererMixin` |
| Disguise: skin, model, nametag | `mixin/client/AbstractClientPlayerEntityMixin`, `PlayerEntityDisplayNameMixin`, `client/DisguiseHelper` |
| Proximity chat with identity spoofing | `game/ProximityChat` |
| Simple Voice Chat plugin | `compat/voicechat/FRVoicechatPlugin` |
| Post-processing: mirror grade, sanity warp, flashlight cone | `client/render/FRPostEffects`, `shaders/program/composite.fsh`, `mixin/client/GameRendererMixin` |
| Mirror surface rendering | `client/render/MirrorBlockEntityRenderer` |
| Mirror Dimension atmosphere | `client/MirrorAtmosphere`, `client/render/MirrorDimensionEffects`, `client/sound/MuffleFilter` + `SourceMixin` |

### How the key mechanics work

* **Instant synchronisation.** Every successful `World#setBlockState` in one arena world is replayed at
  the same position in the twin world on the same tick. That covers breaks, placements, doors, candles,
  blackouts and the mirror `ACTIVE` state. A thread-local guard stops the echo from bouncing back.
  Blocks tagged `fractured_reflections:unsynced` (puzzle pieces, escape mirrors) are one-sided.
* **Entity Phasing.** `MirrorBlock#getCollisionShape` returns an empty shape for The Fractured on both
  logical sides (`RoleRegistry`), so client prediction agrees with the server. Touching the mirror moves
  it out of the twin mirror and broadcasts a Mirror Crawl animation.
* **Portal Transformation.** It flips `ACTIVE` on the whole mirror column, and sync opens the twin too.
  With Immersive Portals it spawns a two-way IP portal pair rotated 180° (so you step *out* of the twin
  mirror). Without it, contact teleports and the vanilla end-portal shader draws the rift.
* **Identity spoofing.** Chat during a match is re-sent as system messages with the disguise's name. Voice
  is already attached to the speaker's entity in Simple Voice Chat, and that entity renders with the copied
  skin, model and nametag, so the talk icon shows up over "the survivor". The plugin also silences the
  undisguised killer and lets trapped survivors' voices leak faintly into the Main World.
* **Ghost Echoes.** These are client-only `OtherClientPlayerEntity` copies that are "invisible but not to
  the viewer". Vanilla's renderer then draws them with its built-in 15 % alpha ghost pass.

---

## Premade assets only

No textures, sounds or models were authored for this mod:

* **Audio:** each of the 28 sound events in `assets/fractured_reflections/sounds.json` points at a
  vanilla sound event (`"type": "event"`), such as the warden heartbeat, glass break, cave ambience,
  illusioner mirror move, bell and end-portal spawn. To use a licensed SFX pack instead, replace an
  entry with `{"name": "fractured_reflections:heartbeat_01"}` and drop the `.ogg` files into
  `assets/fractured_reflections/sounds/`.
* **Textures and models:** block and item models reference vanilla textures (glass, ice, gold, enchanting
  table, spyglass, amethyst shard...). Override any of them from a resource pack.
* **Shaders:** a single small GLSL program, `composite.fsh`, runs on vanilla's `sobel` vertex shader and
  `PostEffectProcessor`. It is skipped entirely when idle. Mirror surfaces reuse vanilla's
  enchantment-glint and end-portal shaders. Real-time reflections are optional through Immersive Portals
  `Mirror` entities (`immersivePortalsReflections`, off by default for mid-range GPUs).

## Integrations

| Mod | How it's used | Required? |
|---|---|---|
| Fabric API | networking, events, commands, rendering, keybinds, dimensions, item groups | yes |
| Immersive Portals (`imm_ptl_core`) | seamless portal pairs and optional true reflections. It is bound **reflectively**, so the build has no compile dependency and keeps working across IP API renames. | recommended |
| LambDynamicLights | flashlight light source with proximity flicker (`lambdynamiclights` entrypoint) | recommended |
| Simple Voice Chat | voice identity, killer silence and mirror voice leak (`voicechat` entrypoint, compile-only API) | recommended |

Dependency versions are in `gradle.properties`.

## Configuration

`config/fractured_reflections.json` is created on first launch. It holds the mansion size and floors,
match length, stamina and sanity rates, every ability cooldown, the blackout radius and restore time, the
cleansing duration and radius, chat range and the integration toggles.

## Verification notes

These parts were written against my knowledge of the 1.20.1 Yarn mappings and the integration APIs, and
have not been compiled. Check them first if the build complains:

* Yarn names used in the mixins: `GameRenderer#render` (injected after
  `WorldRenderer#drawEntityOutlinesFramebuffer`), `JsonEffectShaderProgram#<init>/loadEffect`,
  `PostEffectProcessor.passes`, `Source#play` / `Source.pointer`, `ClientPlayerEntity#canSprint`.
* `voicechat-api` 2.5.0: `MicrophonePacket#toLocationalSoundPacketBuilder`,
  `VoicechatServerApi#sendLocationalSoundPacketTo`.
* LambDynamicLights 2.3.x: `DynamicLightsInitializer`, `DynamicLightHandlers`,
  `DynamicLightHandler.makeHandler`.
* Maven versions in `gradle.properties` (LambDynamicLights, Simple Voice Chat and Immersive Portals
  release strings on Modrinth).

`JsonEffectShaderProgramMixin` uses `require = 0`. If its target ever moves, the post effect is disabled
and the game keeps running.
