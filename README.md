# DroidMaker

A Game Maker 8–style game engine and editor that runs entirely on an Android phone.
Build games with objects, events and drag-and-drop actions (or code), paint sprites,
generate sounds, lay out rooms with your finger, and press ▶ to play — no PC needed.

## Features

**Three kinds of rooms**
- **2D** – sprites, tiles, scrolling backgrounds, views/cameras, particles.
- **2.5D raycast** – Wolfenstein-style first person. Mark an object as *Raycast wall* and
  its instances become textured walls on the room grid; other sprites become billboards.
  Textured floor/ceiling, fog, minimap-friendly functions (`rc_wall_at`, `rc_cast`, `rc_can_see`).
- **True 3D** (OpenGL ES 2.0) – blocks, floors, walls, cylinders, cones, spheres, models,
  transforms, directional/point lights, fog, first/third-person cameras, billboards. Objects can
  simply be set to “Block” or “Billboard” so a level can be built in the room editor.

**Game Maker 8 logic model**
- Events: Create, Destroy, Alarm 0–11, Begin/Step/End step, Collision, Keyboard (held/pressed/released),
  Touch (tap on instance, held, released, enter/leave, global, swipes, double tap, long press),
  Other (outside room, boundary, game/room start/end, animation end, no lives/health, user events),
  Draw and Draw GUI.
- Parents with event inheritance (`event_inherited()`), solid objects, depth, persistence,
  collision masks, views that follow objects.
- 140+ drag-and-drop actions in 10 categories (Move, Main, Game, Control, Variables, Score, Draw,
  Touch, 3D, Raycast) including questions, blocks, else, repeat, “applies to other/object”,
  relative, NOT — plus ready-made platformer, top-down and FPS controls.
  Drag tiles from the palette into the event, hold an action to drag it, drop on the bar to delete.

**Script language (GML-like)**
`var`, `globalvar`, `if/else`, `while`, `do/until`, `for`, `repeat`, `with`, `switch`, `break`,
`continue`, `exit`, `return`, arrays (`a[i]`, `a[i,j]`, `[1,2,3]`), `other.x`, `global.x`,
`obj.x`, scripts with `argument0..15`, and nearly 500 built-in functions (math, strings, instances,
collisions, motion, drawing, text, colors, sound, input/touch/virtual gamepad, views, effects,
ds_list/map/grid/stack/queue, ini & text files, saving, game save/load, JSON, d3d_*, rc_*).
Colors are written `0xRRGGBB` (`c_red = 0xFF0000`).

**Phone-first editors**
- Project tree with folders, rename (references in code are updated), duplicate, delete, room order.
- Pixel sprite editor: pencil, eraser, line, rectangles, ellipses, fill, picker, move, mirror,
  onion skin, frames, animation preview, gallery import, strip slicing, origin & collision mask.
- Sound editor: import wav/ogg/mp3 or generate retro effects (sfxr-style synth with presets).
- Code editor: syntax colors, line numbers, autocomplete with signatures, symbol bar, snippets,
  error checking that jumps to the line.
- Room editor: paint/select/erase/pan tools, snap & grid, pinch zoom, tiles, instance creation
  code, undo/redo, full room/view/raycast/3D settings, “play from this room”.
- Game settings: resolution, orientation, fit/stretch/expand scaling, on-screen d-pad or
  joystick with up to 4 buttons mapped to keys.
- Projects export/import as `.dmk.zip` files.

**Demo projects** (created on first launch): a platformer, a raycast maze and a 3D world.

## Building

Push to `main` and GitHub Actions runs the headless engine tests, builds the APK
and attaches `DroidMaker.apk` to a new release. Locally:

```
./gradlew assembleRelease        # needs the Android SDK
```

Engine tests run on any JVM, no Android needed:

```
find app/src/main/java/com/droidmaker/core -name '*.java' > core.txt
javac -d out @core.txt tools/coretest/CoreTest.java && java -cp out CoreTest
```

## Layout

| Path | What |
|---|---|
| `core/lang` | Lexer, parser, interpreter, built-in registry |
| `core/project` | Project model, events, action library, D&D → code generator |
| `core/runtime` | Game loop, instances, collisions, views, raycaster, 3D state, all functions |
| `core/sfx`, `core/demo` | Sound synthesizer, demo project builders |
| `run` | Android runner: OpenGL renderer, text atlas, sound, virtual gamepad |
| `app` | Editors and project management UI |

The `core` packages are plain Java, so the whole engine is tested headlessly on the desktop.
