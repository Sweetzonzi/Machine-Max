# subsystem/ — Subsystem Implementations

**Scope**: 18 subsystem types, their attributes, and the base class hierarchy.

## STRUCTURE

```
subsystem/
├── AbstractSubsystem.java              # Base: durability, signals, energy, save/load
├── AbstractControllableSubsystem.java  # Adds player control input handling
├── BasicSubsystem.java                 # Minimal (destructibility only)
├── SubsystemController.java            # Ticks all subsystems, manages EnergyGrid
├── attr/
│   ├── SubsystemTypes.java             # Enum of all 18 types
│   ├── static_attr/                    # 21 files: JSON-driven static attributes
│   └── dynamic_attr/                   # 20 files: runtime mutable attributes
├── EngineSubsystem.java                # ICE with RPM torque curve
├── MotorSubsystem.java                 # Electric motor (coupleTorque=0, disabled)
├── GearboxSubsystem.java               # Multi-ratio transmission
├── TransmissionSubsystem.java          # Transfer case/differential
├── WheelDriverSubsystem.java           # Drive + steering servo
├── JointDriverSubsystem.java           # Generic joint actuator
├── TurretDriverSubsystem.java          # Azimuth/elevation servos
├── CarControllerSubsystem.java         # Interprets player input
├── MotorbikeControllerSubsystem.java   # Motorcycle controls
├── WeaponControllerSubsystem.java      # Targeting + fire commands
├── SignalConvertSubsystem.java         # Signal renaming/mapping
├── SeatSubsystem.java                  # Passenger mount, input routing
├── BatterySubsystem.java               # Energy storage
├── ItemStorageSubsystem.java           # Inventory container
├── CameraSubsystem.java                # Onboard camera
├── LightingSubsystem.java              # Volumetric light (client)
├── ScriptableSubsystem.java            # JavaScript-driven logic
└── LauncherSubsystem.java              # Projectile launcher
```

## WHERE TO LOOK

| Task | File | Notes |
|------|------|-------|
| Add new subsystem type | `AbstractSubsystem.java` | Extend, register in SubsystemTypes |
| Fix engine behavior | `EngineSubsystem.java` | RPM torque curve, sound states |
| Fix motor behavior | `MotorSubsystem.java` | coupleTorque=0 (disabled bug) |
| Fix transmission | `GearboxSubsystem.java` | Auto-shift, ratio logic |
| Fix vehicle control | `CarControllerSubsystem.java` | Throttle, steering, braking PID |
| Fix seat interaction | `SeatSubsystem.java` | Passenger mount, input signals |
| Fix energy storage | `BatterySubsystem.java` | Charge/discharge, grid integration |
| Fix projectile launch | `LauncherSubsystem.java` | Ammo consumption, projectile type |
| Add static attributes | `attr/static_attr/` | JSON codec, per-type definition |
| Add dynamic attributes | `attr/dynamic_attr/` | Runtime mutable state |

## CONVENTIONS

- **Inheritance**: `AbstractSubsystem` → `AbstractControllableSubsystem` → specific type.
- **Signal I/O**: All implement `ISignalReceiver`/`ISignalSender`. Channels are `ConcurrentMap<String, SignalChannel>`.
- **Power flow**: `Engine/Motor` (IMechPowerProducer) → `Gearbox` → `Transmission` → `WheelDriver/JointDriver` via `MechPowerPort` on connectors.
- **Energy grid**: `BatterySubsystem` implements both `IEnergyProducer` and `IEnergyConsumer`. `EnergyGrid` balances per-tick.
- **Save/load**: Override `saveData()`/`loadData()` in `AbstractSubsystem` for NBT persistence.
- **Threading**: `onCollideWithBlock()` called on physics thread; `onInteract()` on main thread.

## ANTI-PATTERNS

- **Never bypass AbstractSubsystem lifecycle**: Always call super in tick/prePhysicsTick/postPhysicsTick.
- **Never ignore `coupleTorque` disable**: MotorSubsystem has it hard-coded to 0 due to oscillation. Do not re-enable without fixing the root cause.
- **Never add subsystem without static_attr**: Every subsystem type needs a corresponding static attribute class for JSON loading.
- **Never modify `allSubsystems` during iteration**: It's a `CopyOnWriteArraySet` — changes are safe but expensive.
