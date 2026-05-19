# network/ — Network Sync

**Scope**: All payload classes, protocol registration, payload handlers.

## STRUCTURE

```
network/
├── MMPayloadRegistry.java          # Registers all payloads into 4 protocol groups
├── payload/
│   ├── assembly/                   # LevelVehicleData, PlayerPartAssemblyCache, VehicleDataSaved
│   ├── research/                   # FreeRpSync, ResearchCompleteRequest, etc.
│   ├── VehicleCreatePayload.java   # Vehicle lifecycle
│   ├── ConnectorAttachPayload.java # Structure changes
│   ├── SubPartSyncPayload.java     # Delta sync (SynchedEntityData + byte 255)
│   ├── SubsystemSyncPayload.java   # Subsystem state sync
│   ├── MovementInputPayload.java   # Player movement input
│   ├── RegularInputPayload.java    # General action input
│   ├── FabricationStartPayload.java # GUI fabrication
│   ├── ProjectileSpawnPayload.java # Projectile lifecycle
│   └── ...
└── handler/research/               # Research payload handlers
```

## WHERE TO LOOK

| Task | File | Notes |
|------|------|-------|
| Add new payload | `MMPayloadRegistry.java` | Pick correct protocol group |
| Fix vehicle sync | `payload/SubPartSyncPayload.java` | Delta sync, byte 255 terminator |
| Fix input latency | `payload/MovementInputPayload.java` | TODO: test input latency |
| Fix research sync | `payload/research/*.java` + `handler/research/*.java` | Bidirectional, version 2.0.0 |
| Fix assembly sync | `payload/assembly/*.java` | LevelVehicleData, cache sync |
| Fix projectile sync | `payload/ProjectileSpawnPayload.java` | Server→client spawn + hit effects |

## CONVENTIONS

- **4 protocol groups**: `input:1.0.0` (controls), `sync:1.0.0` (world state), `research:2.0.0` (progression), `misc:1.0.0` (GUI/fabrication).
- **ALL handlers on main thread**: Use `MainThreadPayloadHandler`. No physics-thread handlers.
- **Bidirectional payloads**: Use `DirectionalPayloadHandler` with separate client/server methods.
- **Delta sync**: `SubPartSyncPayload` uses `SynchedEntityData.DataValue` list with byte `255` terminator.
- **State sync**: VehicleCore HP, SubPart durability, subsystem durability/active, connector integrity via `SynchedEntityData` + targeted payloads.

## ANTI-PATTERNS

- **Never add physics-thread payload handlers**: ALL handlers must run on main thread.
- **Never break protocol version compatibility**: Research is at 2.0.0 for a reason.
- **Never use old connector ID format**: Future refactor will use SubPart IDs instead of "vehicle UUID-part UUID-connector name".
