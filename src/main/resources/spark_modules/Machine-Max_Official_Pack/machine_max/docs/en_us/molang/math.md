# Math Functions

The `math` object provides mathematical calculation capabilities, including trigonometric functions, exponential functions, rounding functions, and more.

## Trigonometric Functions

### math.sin(value)
Calculates the sine of an angle.

**Parameters**:
- `value` (double): Angle value (degrees)

**Return Value**: Sine value

**Example**:
```molang
math.sin(90)  // Returns 1.0
```

### math.cos(value)
Calculates the cosine of an angle.

**Parameters**:
- `value` (double): Angle value (degrees)

**Return Value**: Cosine value

**Example**:
```molang
math.cos(0)  // Returns 1.0
```

### math.tan(value)
Calculates the tangent of an angle.

**Parameters**:
- `value` (double): Angle value (degrees)

**Return Value**: Tangent value

### math.asin(value)
Calculates the arcsine of a value (returns angle).

**Parameters**:
- `value` (double): Value (-1.0 to 1.0)

**Return Value**: Angle value (degrees)

### math.acos(value)
Calculates the arccosine of a value (returns angle).

**Parameters**:
- `value` (double): Value (-1.0 to 1.0)

**Return Value**: Angle value (degrees)

### math.atan(value)
Calculates the arctangent of a value (returns angle).

**Parameters**:
- `value` (double): Value

**Return Value**: Angle value (degrees)

### math.atan2(y, x)
Calculates the arctangent of y/x (returns angle).

**Parameters**:
- `y` (double): Y coordinate value
- `x` (double): X coordinate value

**Return Value**: Angle value (degrees)

**Example**:
```molang
// Used in AE86 suspension animation
math.atan2(subpart.connector_offset('connector.machine_max.right_front_wheel', 1)*16, 20)
```

## Exponential and Logarithmic Functions

### math.exp(value)
Calculates e raised to the power of value.

**Parameters**:
- `value` (double): Exponent

**Return Value**: e^value

### math.log(value)
Calculates the natural logarithm (base e) of a value.

**Parameters**:
- `value` (double): Value (> 0)

**Return Value**: Natural logarithm

### math.pow(base, exponent)
Calculates base raised to the power of exponent.

**Parameters**:
- `base` (double): Base
- `exponent` (double): Exponent

**Return Value**: base^exponent

### math.sqrt(value)
Calculates the square root of a value.

**Parameters**:
- `value` (double): Value (≥ 0)

**Return Value**: Square root

**Example**:
```molang
// Calculate link length in AE86 suspension animation
math.sqrt(math.pow(20-subpart.connector_offset('connector.machine_max.left_front_wheel', 1)*16, 2)+81)
```

## Rounding Functions

### math.floor(value)
Rounds down to the nearest integer.

**Parameters**:
- `value` (double): Value

**Return Value**: Largest integer less than or equal to value

**Example**:
```molang
// Used in HUD speed display
math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)
```

### math.ceil(value)
Rounds up to the nearest integer.

**Parameters**:
- `value` (double): Value

**Return Value**: Smallest integer greater than or equal to value

### math.round(value)
Rounds to the nearest integer.

**Parameters**:
- `value` (double): Value

**Return Value**: Nearest integer

## Other Mathematical Functions

### math.abs(value)
Calculates the absolute value.

**Parameters**:
- `value` (double): Value

**Return Value**: Absolute value

**Example**:
```molang
// Ensure speed value is positive
math.abs(vehicle.get('vehicle_speed')??0.0)
```

### math.mod(value1, value2)
Calculates the remainder of value1 divided by value2.

**Parameters**:
- `value1` (double): Dividend
- `value2` (double): Divisor

**Return Value**: Remainder

**Example**:
```molang
// Extract tens digit in HUD speed display
math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)
```

### math.sign(value)
Gets the sign of a value.

**Parameters**:
- `value` (double): Value

**Return Value**: 1.0 (positive), -1.0 (negative), 0.0 (zero)

### math.min(value1, value2)
Returns the smaller of two values.

**Parameters**:
- `value1` (double): First value
- `value2` (double): Second value

**Return Value**: Smaller value

### math.max(value1, value2)
Returns the larger of two values.

**Parameters**:
- `value1` (double): First value
- `value2` (double): Second value

**Return Value**: Larger value

### math.clamp(value, min, max)
Clamps a value to the specified range.

**Parameters**:
- `value` (double): Value to clamp
- `min` (double): Minimum value
- `max` (double): Maximum value

**Return Value**: Clamped value

### math.lerp(start, end, alpha)
Linear interpolation.

**Parameters**:
- `start` (double): Start value
- `end` (double): End value
- `alpha` (double): Interpolation coefficient (0.0 to 1.0)

**Return Value**: Interpolation result

## Mathematical Constants

### math.pi
The value of pi (π).

**Type**: Read-only double value

**Value**: Approximately 3.141592653589793

**Example**:
```molang
// Radians to degrees
radians * 180 / math.pi

// Degrees to radians
degrees * math.pi / 180
```

## Practical Usage Examples

### 1. Unit Conversion
```molang
// Meters per second to kilometers per hour
vehicle.get('vehicle_speed') * 3.6

// Radians per second to revolutions per minute (RPM)
vehicle.get('engine_speed') * 30 / math.pi
```

### 2. Animation Control
```molang
// Smooth animation interpolation
math.lerp(current_angle, target_angle, 0.1)

// Limit rotation angle to reasonable range
math.clamp(rotation_angle, -180, 180)
```

### 3. Physical Calculations
```molang
// Calculate distance between two points
math.sqrt(math.pow(x2-x1, 2) + math.pow(y2-y1, 2) + math.pow(z2-z1, 2))

// Calculate angle difference
math.abs(math.mod(angle1 - angle2 + 180, 360) - 180)
```

## Notes

1. **Angle Units**: All trigonometric functions use degrees as units, not radians
2. **Performance Considerations**: Complex mathematical calculations may affect performance, simplify expressions when possible
3. **Numerical Range**: Pay attention to function domains and ranges to avoid mathematical errors
4. **Precision Issues**: Floating-point calculations may have precision errors, perform appropriate rounding for important calculations