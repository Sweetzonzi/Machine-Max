# Vehicle Methods

The `vehicle` object provides methods to access global vehicle status and signals.

## vehicle.get(channel)

Retrieves the value from the vehicle's signal channel.

### Syntax
```molang
vehicle.get(channel_name)
```

### Parameters
- `channel_name` (string): Signal channel name

### Return Value
- Signal value, returns `null` if the channel doesn't exist

### Examples
```molang
// Get steering signal
vehicle.get('steering')

// Get throttle signal
vehicle.get('throttle')

// Get brake signal
vehicle.get('brake')

// Get vehicle speed (meters per second)
vehicle.get('vehicle_speed')

// Get engine speed (radians per second)
vehicle.get('engine_speed')
```

## Common Signal Channels

### Control Signals
- `steering`: Steering input, range [-1.0, 1.0]
- `throttle`: Throttle input, range [0.0, 1.0]
- `brake`: Brake input, range [0.0, 1.0]
- `handbrake`: Handbrake input, range [0.0, 1.0]

### Status Signals
- `vehicle_speed`: Vehicle speed (meters per second)
- `engine_speed`: Engine speed (radians per second)
- `gear`: Current gear
- `rpm`: Engine speed (revolutions per minute)

### System Signals
- `fuel_level`: Fuel level
- `temperature`: Engine temperature
- `battery_voltage`: Battery voltage

## Practical Usage Examples

### 1. Steering Wheel Animation Control
Use steering signal to control steering wheel rotation in AE86 animation file:

```json
{
  "rot2": {
    "rotation": [0, 0, "vehicle.get('steering')*225"]
  }
}
```

**Explanation**: 
- `vehicle.get('steering')` retrieves steering input value (-1.0 to 1.0)
- Multiply by 225 degrees to convert input value to rotation angle
- When steering input is -1.0 (left turn), steering wheel rotates -225 degrees
- When steering input is 1.0 (right turn), steering wheel rotates 225 degrees

### 2. HUD Tachometer Pointer Control
Use engine speed to control pointer deflection in example HUD animation:

```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

**Explanation**:
- `vehicle.get('engine_speed')` retrieves engine speed (radians per second)
- `??0` is null coalescing operator, uses 0 if signal doesn't exist
- `*30/math.pi` converts radians per second to revolutions per minute (RPM)
- `/9000` normalizes to maximum speed of 9000 RPM
- `*240` converts normalized value to pointer rotation angle (240 degree full scale)

### 3. HUD Speed Display
Calculate display values using speed signal in HUD configuration:

```json
{
  "speed": {
    "molang_args": [
      "math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)",
      "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
      "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))"
    ]
  }
}
```

**Explanation**:
- `vehicle.get('vehicle_speed')` retrieves vehicle speed (meters per second)
- `* 3.6` converts meters per second to kilometers per hour
- First parameter: Calculate hundreds digit (speed divided by 100, floored)
- Second parameter: Calculate tens digit (speed modulo 100, divided by 10, floored)
- Third parameter: Calculate units digit (speed modulo 10, floored)

## Notes

1. **Null Value Handling**: Use `??` operator to handle potentially missing signal channels
2. **Unit Conversion**: Pay attention to units of different signals (rad/s vs RPM, m/s vs km/h)
3. **Range Limitation**: Some signals have specific value ranges that require appropriate scaling in animations
4. **Performance Considerations**: Complex Molang expressions may affect performance, keep them concise