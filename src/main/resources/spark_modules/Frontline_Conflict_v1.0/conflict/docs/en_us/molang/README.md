# Molang Documentation

Machine-Max uses the Molang scripting system to control vehicle animations, HUD displays, and interaction logic. Molang is a lightweight scripting language specifically designed for real-time calculations and animation control.

## Table of Contents

- [Subpart Methods](./subpart.md) - Methods and properties related to parts
- [Vehicle Methods](./vehicle.md) - Methods and properties related to vehicles
- [Math Functions](./math.md) - Mathematical calculation functions
- [Usage Examples](./examples.md) - Practical usage examples

## Basic Syntax

Molang expressions use JavaScript-like syntax, supporting basic mathematical operations, conditional judgments, and function calls.

### Basic Operations
```molang
// Arithmetic operations
1.0 + 2.0 * 3.0  // Result: 7.0
(1.0 + 2.0) * 3.0  // Result: 9.0

// Comparison operations
speed > 0  // Greater than comparison
durability <= max_durability  // Less than or equal to comparison
v.a == 3.0  // Equal to comparison
```

### Ternary Expressions (Conditional Operator)
```molang
// Basic syntax: condition ? true_value : false_value
variable > 0 ? 1.0 : 0.0

// Practical application: warning light based on subsystem status
subpart.has_subsystem('engine') && subpart.subsystem_destroyed('engine') ? 1.0 : 0.0

// Nested usage
v.fuel_level > 50 ? 0.0 : (v.fuel_level > 10 ? 0.5 : 1.0)
```

### Null Coalescing Operator (??)
```molang
// Basic syntax: value ?? default_value
vehicle.get('signal_name')??0.0

// Practical application: handling potentially missing signals
math.abs(vehicle.get('vehicle_speed')??0.0) * 3.6

// Chained usage
vehicle.get('engine_speed')??vehicle.get('motor_speed')??0.0
```

### Function Calls
```molang
// Math functions
math.sin(angle)
math.floor(value)

// Vehicle and part methods
vehicle.get('steering')
subpart.has_subsystem('engine')
```

## Common Patterns

### Signal Retrieval and Safe Access
```molang
// Safe signal value retrieval
vehicle.get('signal_name')??default_value

// Conditional access after checking
subpart.has_subsystem('engine') ? subpart.subsystem_durability('engine') : 0.0
```

### Unit Conversion
```molang
// Meters per second to kilometers per hour
vehicle.get('vehicle_speed')??0.0 * 3.6

// Radians per second to revolutions per minute (RPM)
vehicle.get('engine_speed')??0.0 * 30 / math.pi
```

## Next Steps

- View [Subpart Methods](./subpart.md) to learn about all part-related methods
- View [Vehicle Methods](./vehicle.md) to learn about vehicle signal retrieval methods
- View [Math Functions](./math.md) to learn about available mathematical calculation functions
- View [Usage Examples](./examples.md) to learn practical application examples