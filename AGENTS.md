# Machine-Max — Agent Guide

**Generated:** 2026-05-17 · **Commit:** f9ca6dc7 · **Branch:** 1.21.1

## OVERVIEW

NeoForge 1.21.1 Minecraft vehicle mod. Kotlin + Java, Gradle 8.9, JDK 21. Data-driven part-based vehicle assembly with real-time physics (Bullet via Spark-Core), 18 subsystem types, and content pack extensibility.

## STRUCTURE

```
io.github.sweetzonzi.machine_max/
├── MachineMax.java              # mod init, ObjectRegister hub
├── common/
│   ├── mech/                    # CORE DOMAIN — see common/mech/AGENTS.md
│   │   ├── vehicle/             # VehicleCore, Part, SubPart, connectors, signals, energy
│   │   ├── subsystem/           # 18 subsystem types — see subsystem/AGENTS.md
│   │   ├── ObjectManager.java   # per-dimension VehicleCore registry
│   │   ├── DestroyableObject.java / DestroyableRigidObject.java
│   │   └── projectile/          # data-driven projectile system (SoA)
│   ├── item/                    # PartItem, AssemblyItem, WeldingTorch, Crowbar, Blueprint
│   ├── block/                   # Fabricator, ResearchTable, TotalStation, RoadBase
│   ├── recipe/                  # ResearchRecipe, FabricatingRecipe
│   ├── attachment/              # VehicleAssemblyAttachment, BlueprintAttachment
│   ├── registry/                # MMItems, MMBlocks, MMEntities (Kotlin)
│   └── visual/                  # PartAnimatable, VehicleAnimatable, AnimatableParams
├── client/                      # rendering, input, GUI, HUD — see client/AGENTS.md
├── network/                     # payload classes + MMPayloadRegistry — see network/AGENTS.md
├── mixin/                       # 6 server + 6 client mixins
├── util/                        # PD/PID, terrain (LocalHeightField), MMMath
├── external/                    # MMDynamicRes (Spark-Core resource loading)
├── datagen/                     # data generator (Kotlin)
└── compat/                      # Create mod interop
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add/modify vehicle physics | `common/mech/vehicle/` | VehicleCore, SubPart, Part, connectors |
| Add new subsystem type | `common/mech/subsystem/` | Extend AbstractSubsystem |
| Add network packet | `network/` | Register in MMPayloadRegistry |
| Add client GUI/HUD | `client/render/gui/` | Screen + HUD + hud3d |
| Add item/block/entity | `common/registry/` (Kotlin) | MMItems.kt, MMBlocks.kt, MMEntities.kt |
| Add content pack data | `src/main/resources/spark_modules/` | JSON in Official_Pack or new pack |
| Add recipe | `common/recipe/` + `resources/spark_modules/.../recipe/` |
| Fix rendering | `client/render/renderer/` | PartEntityRenderer is main vehicle renderer |
| Fix collision | `common/mech/vehicle/collision/` | CollisionHandler + CollisionEffectManager |
| Fix input/control | `client/input/` + `common/mech/subsystem/CarControllerSubsystem.java` |

## COMMANDS

```bash
./gradlew build              # Build mod JAR
./gradlew runClient          # Launch client
./gradlew runServer          # Launch dedicated server
./gradlew runGameTestServer  # Run NeoForge game tests
./gradlew runData            # Regenerate src/generated/resources/
```

## CONVENTIONS

- **Kotlin for declarations, Java for logic**: Registry files (MM*.kt), datagen, resource modules in Kotlin; vehicle core, physics, networking, rendering in Java.
- **ObjectRegister pattern**: Single `MachineMax.REGISTER` field handles all NeoForge registrations via Spark-Core.
- **Thread annotations**: Methods document calling thread in Javadoc — `主线程` (main) vs `物理线程` (physics).
- **Content pack everything**: Parts, subsystems, connectors, recipes, blueprints defined in JSON under `spark_modules/`.

## ANTI-PATTERNS (THIS PROJECT)

- **Never mix JME and JOML math directly**: Physics uses `com.jme3.math.*`, rendering uses `org.joml.*`. Always convert via `SparkMathKt.*`.
- **Never use `synchronized` outside `partNet`**: Only 2 `synchronized` blocks exist (VehicleCore.java:893,972) guarding the Guava `MutableNetwork`. All other shared state uses `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `volatile`, or `CopyOnWriteArraySet`.
- **Never call physics-body mutations from main thread directly**: Use `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`.
- **README package map is wrong**: README says `common/vehicle/` but actual package is `common/mech/vehicle/`.

## UNIQUE STYLES

- **ConcurrentLinkedQueue accumulator pattern**: Physics threads enqueue damage/impact/integrity changes; main thread drains in `handleAccumulated*()`.
- **Volatile snapshot pattern**: `CollisionEffectManager.latestWheelSnapshot` — physics writes, main reads, latest-value-only.
- **SoA projectile arrays**: `ProjectileManager` uses primitive arrays (`posX[]`, `velX[]`, etc.) with swap-remove O(1) deletion.
- **Dual-math conversion files**: 9 files import both JME and JOML (see MMMath, PosRot, VehicleAnimatable, SubPartAnimatable, RenderableBoundingBox, AnimatableParams, EntityMixin, ProjectileSpawnPayload).

## NOTES

- **Composite builds**: `settings.gradle` conditionally includes `../Spark-Core` and `../BallisticsFramework` for local dev. Falls back to Maven jars in CI.
- **No unit tests**: Only NeoForge runtime game tests (`runGameTestServer`). No `src/test/` directory.
- **CI uses JDK 17** while build targets Java 21 bytecode.
- **24 TODOs in MachineMax.java** — full roadmap including network refactor, turret control, mech suit, and more.
- **Known crash**: Multi-threaded physics + joints = crash (VehicleCore.java:1038). Sequential addToLevel is the workaround.
- **Coupling torque disabled**: `MotorSubsystem.coupleTorque = 0` due to wheel oscillation at stop.

## REFERENCE FILES

- `docs/LLM_QUICKSTART.md` — deep dive into assembly pipeline, signal system, network sync
- `docs/glossary.md` — auto-generated term dictionary (287 lines)
- `docs/武器系统-数据驱动投射物设计文档.md` — projectile system design doc (Chinese)
