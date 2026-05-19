# Machine-Max — Agent Guide

## Project

NeoForge 1.21.1 Minecraft vehicle mod. Kotlin + Java, Gradle 8.9, JDK 21.

- **Group**: `io.github.sweetzonzi.machine_max` · **Mod ID**: `machine_max`
- **Entry**: `src/main/java/io/github/sweetzonzi/machine_max/MachineMax.java` — registration hub via `REGISTER` (ObjectRegister)
- **ModLoader**: `kotlinforforge` (Kotlin for Forge — mods.toml uses this)

## Key Commands

| Command | Purpose |
|---|---|
| `./gradlew build` | Build the mod (jar in `build/libs/`) |
| `./gradlew runClient` | Launch Minecraft client |
| `./gradlew runServer` | Launch dedicated server |
| `./gradlew runGameTestServer` | Run game tests |
| `./gradlew runData` | **Regenerate** `src/generated/resources/` |

## Composite Builds (local dev)

`settings.gradle` conditionally includes sibling directories for source-level dependency:

- `../Spark-Core` (Spark-Core content pack framework)
- `../BallisticsFramework` (physics/ballistics)

If absent, falls back to published Maven jars. CI uses published jars.

## Actual Package Layout

Source has been **refactored**: `common/mech/` (not `common/vehicle/` as the README says — README is outdated).

```
io.github.sweetzonzi.machine_max/
├── MachineMax.java          # mod init, registers everything
├── common/                  # server & shared
│   ├── mech/                # core vehicle domain (was vehicle/)
│   │   ├── vehicle/         # VehicleCore, Part, SubPart, connectors, signals, energy
│   │   ├── subsystem/       # Engine, Gearbox, Seat, Battery, etc.
│   │   ├── ObjectManager.java  # global per-dimension VehicleCore registry
│   │   ├── DestroyableObject.java / DestroyableRigidObject.java
│   │   └── projectile/      # data-driven projectile system (WIP)
│   ├── item/                # PartItem, AssemblyItem, WeldingTorchItem, CrowbarItem, etc.
│   ├── block/               # Fabricator, ResearchTable, TotalStation, RoadBase
│   ├── recipe/              # ResearchRecipe, FabricatingRecipe
│   ├── attachment/          # VehicleAssemblyAttachment (per-player assembly state)
│   ├── registry/            # MMItems, MMBlocks, MMEntities, etc.
│   └── visual/              # PartAnimatable, VehicleAnimatable
├── client/                  # rendering, input, GUI, HUD
├── network/                 # payload classes + MMPayloadRegistry
├── mixin/                   # 6 server + 6 client mixins (machine_max.mixins.json)
├── util/                    # PD/PID controllers, terrain (LocalHeightField), MMMath
├── external/                # MMDynamicRes (Spark-Core integration)
├── datagen/                 # data generator
└── compat/                  # Create mod interop
```

## Math Library Duality (critical)

Two incompatible math libraries coexist — **never mix them directly**:

| Domain | Library | Key Types |
|---|---|---|
| **Physics** (JME) | `com.jme3.math` | `Vector3f`, `Quaternion`, `Transform`, `Matrix4f` |
| **Rendering** (JOML) | `org.joml` | `Vector3f`, `Quaternionf`, `Matrix4f` |

**Always use `SparkMathKt.*` (from Spark-Core) for cross-type conversion** (e.g., `toBVector3f`, `toQuaternionf`).

## Content Pack System

Data-driven content via Spark-Core JSON modules. All definition files are in `src/main/resources/spark_modules/`:

- `Machine-Max_Official_Pack/` — official parts, connectors, subsystems, assemblies, blueprints
- `machine_max.builtin/` — core models and textures
- `Machine-Max_Pack_Template/` — template for UGC creators

JSON schemas are at `Machine-Max_Official_Pack/docs/zh_cn/schema/`.

## Architecture in Brief

- **Part** — assembly/recipe granularity, holds SubParts, manages recipes and material progress
- **SubPart** — physics/functional granularity: rigid body, collision, subsystems, connectors
- **VehicleCore** — aggregate root: partNet (topology), mass, HP, tick via ObjectManager
- **SignalChannel/SignalPort** — cross-part communication (input, control, power signals)
- **EnergyGrid** — electrical bus; **MechPowerPort** — mechanical power transfer
- **VehicleAssemblyAttachment** — per-player state cache for assembly: current part type, variant, connector, rotation

## Testing

`./gradlew runGameTestServer` runs NeoForge's game test framework.  
Registered under `neoforge.enabledGameTestNamespaces = machine_max`.

## CI / Docs

- **Build**: `.github/workflows/build.yml` — on push/PR
- **Wiki**: MkDocs + Material theme, deployed to GitHub Pages. Build with `mkdocs build --config-file docs/mkdocs.yml`
- **Pages deploy**: only from `1.21.1` branch, triggered by `docs/**` or `.github/**` changes

## Existing Reference Files

- `docs/LLM_QUICKSTART.md` — deep dive into assembly pipeline (VehicleCore, attachment, signal system, network sync)
- `docs/glossary.md` — auto-generated term dictionary covering all major classes
