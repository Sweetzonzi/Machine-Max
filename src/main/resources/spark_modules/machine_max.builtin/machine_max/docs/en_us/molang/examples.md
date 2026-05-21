# Molang Usage Examples

This document provides practical usage examples of Molang in Machine-Max, helping to understand how to apply Molang in real projects.

## Example 1: AE86 Suspension System Animation

### File Location
`machine_max/animations/part/ae86/ae86.animation.json`

### Code Example
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

### Technical Analysis

1. **Suspension Height Retrieval**:
   ```molang
   subpart.connector_offset('connector.machine_max.left_front_wheel', 1)
   ```
   - Gets Y-axis offset of left front wheel connector (suspension height)
   - Return value in meters

2. **Suspension Angle Calculation**:
   ```molang
   math.atan2(20 - height * 16, 9)
   ```
   - Uses arctangent function to calculate suspension link angle
   - `20` is base height, `9` is horizontal distance
   - `height * 16` is unit conversion (meters to game units)

3. **Link Length Calculation**:
   ```molang
   math.sqrt(math.pow(20 - height * 16, 2) + 81) / 22
   ```
   - Uses Pythagorean theorem to calculate link length
   - `81` is square of horizontal distance (9²)
   - `/22` normalizes to base length

### Physical Principle
This suspension system simulates real double-wishbone suspension:
- When wheels move up and down, connector offset changes
- Calculate suspension link angles and lengths using trigonometric functions
- Achieve realistic suspension kinematics

## Example 2: HUD Tachometer Pointer Control

### File Location
`machine_max/animations/hud/example_hud/example_hud.animation.json`

### Code Example
```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

### Technical Analysis

1. **Engine Speed Retrieval**:
   ```molang
   vehicle.get('engine_speed')??0
   ```
   - Retrieves engine speed signal
   - `??0` is null coalescing operator, ensures default value

2. **Unit Conversion**:
   ```molang
   math.abs(engine_speed) * 30 / math.pi
   ```
   - `math.abs()` ensures speed is positive
   - `* 30 / math.pi` converts radians per second to revolutions per minute (RPM)
   - Conversion formula: RPM = (rad/s) × 60 / (2π) = (rad/s) × 30 / π

3. **Normalization**:
   ```molang
   normalized_rpm = rpm / 9000
   ```
   - Assumes maximum speed of 9000 RPM
   - Normalizes actual speed to 0-1 range

4. **Pointer Angle Calculation**:
   ```molang
   pointer_angle = normalized_rpm * 240
   ```
   - Assumes tachometer full scale of 240 degrees
   - Converts normalized speed to pointer rotation angle

### Instrument Design
- **Range**: 0-9000 RPM
- **Pointer Range**: 0-240 degrees
- **Idle Protection**: Uses `??0` to handle signal loss

## Example 3: HUD Speed Display

### File Location
`machine_max/huds/example_hud.json`

### Code Example
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

### Technical Analysis

1. **Speed Retrieval and Unit Conversion**:
   ```molang
   speed_kmh = math.abs(vehicle.get('vehicle_speed')??0.0) * 3.6
   ```
   - Retrieves vehicle speed (meters per second)
   - Converts to kilometers per hour: km/h = m/s × 3.6

2. **Hundreds Digit Extraction**:
   ```molang
   hundreds = math.floor(speed_kmh / 100)
   ```
   - Calculates hundreds digit of speed
   - Example: 123 km/h → hundreds digit is 1

3. **Tens Digit Extraction**:
   ```molang
   tens = math.floor(math.mod(speed_kmh, 100) / 10)
   ```
   - First modulo 100 to get last two digits
   - Then divide by 10 and floor to get tens digit
   - Example: 123 km/h → modulo 100 gives 23 → divide by 10 gives 2.3 → floor gives 2

4. **Units Digit Extraction**:
   ```molang
   units = math.floor(math.mod(speed_kmh, 10))
   ```
   - Direct modulo 10 to get units digit
   - Example: 123 km/h → modulo 10 gives 3

### Display Logic
This method decomposes three-digit speed into three separate numbers, making it easy to display using three different text elements in HUD, implementing a digital speedometer.

## Example 4: Steering Wheel Animation Control

### File Location
`machine_max/animations/part/ae86/ae86.animation.json`

### Code Example
```json
{
  "rot2": {
    "rotation": [0, 0, "vehicle.get('steering')*225"]
  }
}
```

### Technical Analysis

1. **Steering Signal Retrieval**:
   ```molang
   steering_input = vehicle.get('steering')
   ```
   - Retrieves steering input signal
   - Range: -1.0 (left turn) to 1.0 (right turn)

2. **Angle Conversion**:
   ```molang
   steering_angle = steering_input * 225
   ```
   - Converts input signal to rotation angle
   - 225 degrees is maximum steering angle
   - Left turn: -1.0 × 225 = -225 degrees
   - Right turn: 1.0 × 225 = 225 degrees

### Steering System Design
- **Steering Ratio**: 1:225 (input signal to angle)
- **Steering Range**: ±225 degrees
- **Linear Mapping**: Simple multiplication relationship ensures linear steering response

## Best Practices

### 1. Error Handling
```molang
// Use null coalescing operator
vehicle.get('signal_name')??default_value

// Check subsystem existence
subpart.has_subsystem('engine') ? subpart.subsystem_durability('engine') : 0.0
```

### 2. Performance Optimization
```molang
// Avoid repeated calculations
let speed = math.abs(vehicle.get('vehicle_speed')??0.0) * 3.6;
math.floor(speed / 100), math.floor(math.mod(speed, 100) / 10), math.floor(math.mod(speed, 10))

// Use simple mathematical operations
// Avoid complex trigonometric and exponential operations
```

### 3. Readability
```molang
// Use meaningful variable names
let engine_rpm = math.abs(vehicle.get('engine_speed')??0) * 30 / math.pi;
let max_rpm = 9000;
let pointer_range = 240;
let pointer_angle = (engine_rpm / max_rpm) * pointer_range;

// Add comments to explain complex calculations
```

## Debugging Tips

1. **Step-by-Step Testing**: Break down complex expressions into multiple steps for testing
2. **Boundary Value Testing**: Test minimum, maximum, and boundary conditions
3. **Unit Verification**: Ensure all unit conversions are correct
4. **Performance Monitoring**: Monitor execution performance of complex expressions

These examples demonstrate Molang's powerful application capabilities in Machine-Max, from simple signal mapping to complex physical simulations, Molang provides flexible and efficient solutions.