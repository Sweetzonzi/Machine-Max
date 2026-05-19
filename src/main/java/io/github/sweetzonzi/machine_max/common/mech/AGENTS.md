# common/mech/ — Core Vehicle Domain

**Scope**: Vehicle physics, assembly, subsystems, signals, energy, projectiles.

## STRUCTURE

```
common/mech/
├── vehicle/              # VehicleCore, Part, SubPart, connectors, signals, energy
│   ├── connector/        # AbstractConnector, AdvancedConnector, SimpleConnector
│   ├── signal/           # SignalChannel, SignalPort, ISignalReceiver/Sender
│   ├── energy/           # EnergyGrid, MechPowerPort, IEnergyProducer/Consumer
│   ├── collision/        # CollisionHandler, CollisionEffectManager
│   ├── attr/             # SubPartAttr, VariantAttr, connector/signal attributes
│   ├── data/             # VehicleData, PartData, SubPartData serialization
│   └── event/            # ConnectorAttachEvent, VehicleSpiltEvent
├── subsystem/            # 18 subsystem types — see subsystem/AGENTS.md
├── projectile/           # ProjectileManager (SoA), PointProjectile, RigidProjectile
├── ObjectManager.java    # Per-dimension VehicleCore registry
├── DestroyableObject.java / DestroyableRigidObject.java
└── vehicle/              # VehicleCore (aggregate root)
```

## WHERE TO LOOK

| Task | File | Notes |
|------|------|-------|
| Modify vehicle topology | `VehicleCore.java` | partNet (MutableNetwork), merge/split logic |
| Modify physics body | `SubPart.java` | rigid body, collision, aerodynamics, BallisticsFramework |
| Modify assembly/recipe | `Part.java` | SubPart containment, material progress, durability |
| Add connector type | `connector/AbstractConnector.java` | joint (New6Dof), signal port, integrity |
| Add signal type | `signal/Signal.java` | extend base, add to ISignalReceiver/Sender |
| Modify energy grid | `energy/EnergyGrid.java` | DC bus, priority-based load shedding |
| Modify collision | `collision/CollisionHandler.java` | terrain, entity, vehicle-vehicle, Create compat |
| Add projectile behavior | `projectile/ProjectileManager.java` | SoA arrays, rayTest, BFDamageApi |
| Fix physics thread crash | `VehicleCore.java:1038` | Known: joints + multi-thread = crash |

## CONVENTIONS

- **Thread model**: `主线程` (main, 20tps) vs `物理线程` (physics). Document in Javadoc.
- **Accumulator pattern**: Physics threads enqueue to `ConcurrentLinkedQueue`; main thread drains in `handleAccumulated*()`.
- **Volatile snapshot**: `CollisionEffectManager.latestWheelSnapshot` — physics writes, main reads.
- **Math duality**: Physics uses JME (`com.jme3.math.*`), rendering uses JOML (`org.joml.*`). Convert via `SparkMathKt.*`.
- **SignalChannel extends ConcurrentHashMap**: Thread-safe by inheritance.
- **EnergyGrid**: CopyOnWriteArraySet for producers/consumers; ConcurrentMap for supply ratios.
- **MechPower flow**: Engine/Motor → Gearbox → Transmission → WheelDriver/JointDriver via MechPowerPort on connectors.

## ANTI-PATTERNS

- **Never call physics-body mutations from main thread**: Use `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`.
- **Never use `synchronized` outside `partNet`**: Only 2 blocks in VehicleCore (lines 893, 972). Use concurrent collections everywhere else.
- **Never mix JME and JOML math directly**: Always route through `SparkMathKt.*`.
- **Never ignore `DestroyableRigidObject.updateLock`**: Set `true` during server→syncedData sync to prevent feedback loops.
