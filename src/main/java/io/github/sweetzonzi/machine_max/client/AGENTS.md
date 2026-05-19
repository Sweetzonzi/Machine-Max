# client/ — Rendering, Input, GUI

**Scope**: Client-side rendering, player input, HUDs, screens, camera control.

## STRUCTURE

```
client/
├── render/
│   ├── renderer/         # PartEntityRenderer (main), block entity renderers, projectile, light
│   ├── gui/              # Screens, HUDs, 3D HUD, animation helpers, widgets
│   ├── renderable/       # ModelAnimatable, GuiAnimatable
│   └── MMRenderTypes.java # Custom render types (always-visible lines/solids)
├── input/                # KeyBinding, RawInputHandler, CameraController
├── MachineMaxClient.java # Client entry point
├── ClientSetup.java      # Visual effects init
└── MMClientConfig.java   # Client settings
```

## WHERE TO LOOK

| Task | File | Notes |
|------|------|-------|
| Fix vehicle rendering | `render/renderer/PartEntityRenderer.java` | GeoEntityRenderer, wireframe/texture toggle |
| Fix 3D assembly HUD | `render/gui/hud3d/AssemblyHud3D.java` | 822 lines, camera controls, part placement |
| Fix GUI screen | `render/gui/screen/*.java` | FabricatingScreen, BlueprintResearchScreen, etc. |
| Fix HUD overlay | `render/gui/hud/*.java` | AssemblyHud, CustomHud, InteractHud |
| Fix input handling | `input/RawInputHandler.java` | Mouse/keyboard → network payload |
| Fix camera | `input/CameraController.java` | Seat-based camera, yaw/pitch clamping |
| Add render type | `render/MMRenderTypes.java` | LINES_ALWAYS_VISIBLE, SOLID_ALWAYS_VISIBLE |
| Fix block entity renderer | `render/renderer/block/*.java` | Fabricator, ResearchTable, TotalStation |

## CONVENTIONS

- **Rendering math = JOML only**: `org.joml.*`. Physics data from server is JME — convert via `SparkMathKt.*` before rendering.
- **PartEntityRenderer modes**: wireframe (assembly), full texture (complete), fade-in (new parts), hit flash, destroy fade-out.
- **Input pipeline**: RawInputHandler → KeyBinding → network payload → server signal system.
- **VisualEffectHelper** (in `common/visual/VisualEffectHelper.java`): Stores client-only render objects (attach points, bounding boxes, projections).
- **3D HUD**: Uses `Hud3DRenderer` with custom pose stacks and vertex consumers.

## ANTI-PATTERNS

- **Never use JME math in rendering code**: Only JOML in client/. Server data must be converted.
- **Never call server-only methods from client renderers**: Check `level.isClientSide`.
- **Never ignore `ModelAnimatable` deprecation**: Migrate to `SubPartAnimatable`.
