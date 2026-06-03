# Subpart Methods

The `subpart` object provides methods to access part status, connectors, and subsystems.

## Signal-Related Methods

### subpart.get(channel)

Retrieves the value from the part's signal storage.

**Syntax**: `subpart.get(channel_name)`

**Parameters**:
- `channel_name` (string): Signal channel name

**Return Value**: Signal value, returns `null` if the channel doesn't exist

**Example**:
```molang
// Retrieve specific part signal
subpart.get('custom_signal')
```

## Connector-Related Methods

### subpart.has_connector(connectorName)

Checks if the part has the specified connector and whether the connector is connected to a part.

**Syntax**: `subpart.has_connector(connector_name)`

**Parameters**:
- `connector_name` (string): Connector name

**Return Value**: Returns 1.0 if the connector exists and is connected to a part, otherwise 0.0

**Example**:
```molang
// Check if front wheel connector is connected
subpart.has_connector('connector.machine_max.front_wheel')
```

### subpart.connector_offset(connectorName, axis)

Gets the connector's offset (position).

**Syntax**: `subpart.connector_offset(connector_name, axis)`

**Parameters**:
- `connector_name` (string): Connector name
- `axis` (integer): Coordinate axis (0=X, 1=Y, 2=Z)

**Return Value**: Offset value for the specified axis (in meters), returns 0.0 if the connector doesn't exist

**Example**:
```molang
// Get Y-axis offset of front wheel connector (suspension height)
subpart.connector_offset('connector.machine_max.front_wheel', 1)
```

### subpart.connector_rotation(connectorName, axis)

Gets the connector's rotation angle.

**Syntax**: `subpart.connector_rotation(connector_name, axis)`

**Parameters**:
- `connector_name` (string): Connector name
- `axis` (integer): Rotation axis (0=X, 1=Y, 2=Z)

**Return Value**: Rotation angle for the specified axis (in degrees), returns 0.0 if the connector doesn't exist

## Subsystem-Related Methods

### subpart.has_subsystem(subsystemName)

Checks if the part has a subsystem with the specified name.

**Syntax**: `subpart.has_subsystem(subsystem_name)`

**Parameters**:
- `subsystem_name` (string): Subsystem name

**Return Value**: Returns 1.0 if the subsystem exists, otherwise 0.0

**Example**:
```molang
// Check if engine subsystem exists
subpart.has_subsystem('subsystem.machine_max.engine')
```

### subpart.subsystem_durability(subsystemName)

Gets the current durability of the specified subsystem.

**Syntax**: `subpart.subsystem_durability(subsystem_name)`

**Parameters**:
- `subsystem_name` (string): Subsystem name

**Return Value**: Subsystem durability, returns 0.0 if the subsystem doesn't exist

### subpart.subsystem_max_durability(subsystemName)

Gets the maximum durability of the specified subsystem.

**Syntax**: `subpart.subsystem_max_durability(subsystem_name)`

**Parameters**:
- `subsystem_name` (string): Subsystem name

**Return Value**: Subsystem maximum durability, returns 0.0 if the subsystem doesn't exist

### subpart.subsystem_active(subsystemName)

Checks if the specified subsystem is active.

**Syntax**: `subpart.subsystem_active(subsystem_name)`

**Parameters**:
- `subsystem_name` (string): Subsystem name

**Return Value**: Returns 1.0 if the subsystem is active, otherwise 0.0

### subpart.subsystem_destroyed(subsystemName)

Checks if the specified subsystem is destroyed.

**Syntax**: `subpart.subsystem_destroyed(subsystem_name)`

**Parameters**:
- `subsystem_name` (string): Subsystem name

**Return Value**: Returns 1.0 if the subsystem is destroyed, otherwise 0.0

## Part Properties

### subpart.durability

Current durability of the part.

**Type**: Read-only double value

### subpart.max_durability

Maximum durability of the part.

**Type**: Read-only double value

### subpart.is_destroyed

Whether the part has been destroyed.

**Type**: Read-only double value (1.0=destroyed, 0.0=normal)

## Practical Usage Examples

### 1. Suspension System Animation
Control suspension system using connector offsets in AE86 animation:

```json
{
  "axisRightFront": {
    "rotation": [0, 0, "math.atan2(subpart.connector_offset('connector.machine_max.right_front_wheel', 1)*16,20)"]
  },
  "rodLeftFront": {
    "rotation": ["-65.7725+math.atan2(20-subpart.connector_offset('connector.machine_max.left_front_wheel', 1)*16,9)", 0, 0]
  },
  "rodLeftFrontInner": {
    "scale": [1, "math.sqrt(math.pow(20-subpart.connector_offset('connector.machine_max.left_front_wheel', 1)*16,2)+81)/22", 1]
  }
}
```

**Explanation**:
- Use `subpart.connector_offset` to get Y-axis offset of wheel connectors (suspension height)
- Calculate suspension link angles and lengths using trigonometric functions
- Achieve realistic suspension kinematics

### 2. Subsystem Status Display
Control warning lights in HUD using subsystem status:

```molang
// Engine subsystem status check
subpart.has_subsystem('engine') && subpart.subsystem_active('engine') ? 1.0 : 0.0

// Low fuel warning for fuel subsystem
subpart.has_subsystem('fuel_tank') && subpart.subsystem_durability('fuel_tank') < 10.0 ? 1.0 : 0.0

// Engine overheating warning
subpart.has_subsystem('engine') && subpart.subsystem_destroyed('engine') ? 1.0 : 0.0
```

### 3. Conditional Animation Control
Control part animations based on subsystem status:

```json
{
  "engine_cover": {
    "rotation": [
      "subpart.has_subsystem('engine') ? 45.0 : 0.0", 
      0, 
      0
    ]
  },
  "warning_light": {
    "scale": [
      "subpart.subsystem_destroyed('engine') ? 1.5 : 1.0", 
      "subpart.subsystem_destroyed('engine') ? 1.5 : 1.0", 
      "subpart.subsystem_destroyed('engine') ? 1.5 : 1.0"
    ]
  }
}
```

## Notes

1. **Connector Naming**: Connector names typically follow the `connector.machine_max.xxx` naming convention
2. **Subsystem Naming**: Subsystem names typically follow the `subsystem.machine_max.xxx` naming convention
3. **Coordinate System**: Connector offsets and rotations use local coordinate system
4. **Performance Optimization**: Consider performance impact for frequently called methods