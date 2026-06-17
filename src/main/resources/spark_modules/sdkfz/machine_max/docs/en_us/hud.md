# HUD Development Guide

Machine-Max provides a powerful custom HUD system that allows creating dedicated interface displays for different vehicles and subsystems. The HUD system is based on models, animations, and Molang scripts, enabling dynamic dashboards, status indicators, and information displays.

## HUD System Overview

### Core Component File Paths

Here are the core component file paths for the example HUD, where you can find complete implementation examples:

- **HUD Configuration File**: [huds/example_hud.json](../../huds/example_hud.json)
- **3D Model File**: [models/hud/example_hud.geo.json](../../models/hud/example_hud.geo.json)
- **Animation File**: [animations/hud/example_hud/example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json)
- **Texture File**: [textures/hud/example_hud.png](../../textures/hud/example_hud.png)
- **Language File**: [lang/en_us.json](../../lang/en_us.json)
- **Subsystem Configuration**: [subsystems/default_seat.json](../../subsystems/default_seat.json)
- **Advanced Subsystem Configuration**: [subsystems/senna_gtr/senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json)

## Table of Contents
- [HUD System Overview](#hud-system-overview)
- [HUD Configuration File Structure](#hud-configuration-file-structure)
- [Model and Animation Requirements](#model-and-animation-requirements)
- [Text Display Configuration](#text-display-configuration)
- [Molang in HUD Applications](#molang-in-hud-applications)
- [Subsystem HUD Association](#subsystem-hud-association)
- [Practical Case Analysis](#practical-case-analysis)
- [Best Practices](#best-practices)

## HUD System Overview

Machine-Max's HUD system consists of the following core components:

1. **HUD Configuration** (`.json` file) - Defines basic HUD properties and text display
2. **3D Model** (`.geo.json` file) - Provides HUD geometry and bone structure
3. **Animation File** (`.animation.json` file) - Controls dynamic behavior of HUD elements
4. **Texture File** (`.png` file) - Provides visual appearance of the HUD
5. **Subsystem Configuration** - Associates HUD with specific seat subsystems

## HUD Configuration File Structure

The HUD configuration file is the core of the HUD system, defining basic HUD properties and display behavior.

### Basic Structure
```json
{
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "offset": [95, 55, -135],
  "rotation": [-25, -30, 0],
  "scale": [15, 15, 15],
  "color": [255, 255, 255],
  "alpha": 255,
  "perspective": true,
  "texts": {
    // Text configuration
  },
  "enable_scissor": false,
  "scissor_x": 0,
  "scissor_y": 0,
  "scissor_width": 0,
  "scissor_height": 0
}
```

### Configuration Parameter Description

#### Basic Properties
- **model** (Required): HUD 3D model resource address, e.g., `machine_max:example_hud.geo`
- **animation** (Required): HUD animation resource address, e.g., `machine_max:example_hud`
- **texture** (Required): HUD texture resource address, e.g., `machine_max:textures/hud/example_hud.png`
- **offset** (Optional): Offset relative to screen center, in meters for perspective projection, pixels for orthographic projection
- **rotation** (Optional): Rotation angles in degrees
- **scale** (Optional): Scaling factor
- **color** (Optional): RGB color, default white
- **alpha** (Optional): Transparency, default 255
- **perspective** (Optional): Whether to use perspective projection rendering, default true

#### Text Display
```json
"texts": {
  "locator_name": {
    "key": "translation.key",
    "centered": true,
    "shadow": false,
    "scale": [0.7, 0.7, 0.7],
    "color": [255, 0, 0],
    "molang_args": ["expression1", "expression2"],
    "significand": 0
  }
}
```

- **locator_name**: Locator name, used to control text display position and rotation
- **key**: Translation key for text localization
- **centered**: Whether to center the text, default true
- **shadow**: Whether to render text shadow, default false
- **scale**: Scaling factor, default [1, 1, 1]
- **color**: RGB color
- **molang_args**: Molang parameters for filling placeholder positions in translation text
- **significand**: Number of decimal places to keep when Molang parameter results are numbers, default 0

#### Clipping Region
- **enable_scissor**: Whether to enable clipping rectangle, parts outside the range will be clipped, default false
- **scissor_x**: Clipping rectangle center x coordinate (pixels)
- **scissor_y**: Clipping rectangle center y coordinate (pixels)
- **scissor_width**: Clipping rectangle width (pixels)
- **scissor_height**: Clipping rectangle height (pixels)

## Model and Animation Requirements

### 3D Model Specifications
HUD models use standard Minecraft Bedrock format and need to include the following elements:

1. **Bone Structure**: Each position requiring text display needs a corresponding bone
2. **Locators**: Used to determine text display position and orientation
3. **Geometry**: Defines the visual appearance of the HUD

### Animation File
Animation files control the dynamic behavior of HUD elements and can use Molang expressions to achieve dynamic effects:

```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

## Text Display Configuration

### Basic Text Display
```json
"speed": {
  "key": "hud.machine_max.default_1.speed",
  "centered": true,
  "shadow": true,
  "scale": [2, 2, 2]
}
```

### Dynamic Text Display
Using Molang parameters for dynamic content:
```json
"speed": {
  "key": "hud.machine_max.default_1.speed",
  "molang_args": [
    "math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)",
    "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
    "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))"
  ],
  "significand": 0
}
```

### Gear Display
```json
"gear": {
  "key": "hud.machine_max.default_1.gear",
  "molang_args": ["vehicle.get('gear')??'NO GBX'"],
  "centered": true,
  "shadow": true
}
```

## Molang in HUD Applications

The HUD system fully utilizes Molang's powerful functionality to achieve complex dynamic display effects.

### Common Molang Patterns

#### Signal Acquisition and Unit Conversion
```molang
// Get vehicle speed and convert to km/h
math.abs(vehicle.get('vehicle_speed')??0.0) * 3.6

// Get engine speed and convert to RPM
math.abs(vehicle.get('engine_speed')??0) * 30 / math.pi
```

#### Number Decomposition Display
```molang
// Hundreds digit
math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)

// Tens digit
math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)

// Units digit
math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))
```

#### Conditional Display
```molang
// Display warnings based on subsystem status
subpart.has_subsystem('engine') && subpart.subsystem_destroyed('engine') ? 1.0 : 0.0
```

## Subsystem HUD Association

HUDs are associated with specific seats through the view properties of seat subsystems:

### Seat Subsystem Configuration
```json
{
  "type": "machine_max:seat",
  "views": {
    "enable_first_person": true,
    "first_person_hud": ["machine_max:example_hud"],
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_person_hud": ["machine_max:example_hud"],
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

### View Property Description
- **enable_first_person**: Whether to enable first-person perspective
- **first_person_hud**: List of HUD components in first-person perspective
- **first_person_offset**: First-person perspective offset (x+right, y+up, z+back, in meters)
- **enable_third_person**: Whether to enable third-person perspective
- **third_person_hud**: List of HUD components in third-person perspective
- **follow_vehicle**: Whether camera follows vehicle rotation
- **focus_on_center**: Whether camera focuses on vehicle center
- **distance_scale**: Third-person perspective distance multiplier

## Practical Case Analysis

### Example HUD Analysis
Using `machine_max:example_hud` as an example, analyze its complete implementation. You can view the complete implementation files through the following paths:

#### 1. HUD Configuration ([example_hud.json](../../huds/example_hud.json))

This is a fully functional vehicle dashboard HUD configuration that can simultaneously display key information such as engine speed, transmission gear, and vehicle speed.

```json
{
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "offset": [95, 55, -135],
  "rotation": [-25, -30, 0],
  "scale": [15, 15, 15],
  "perspective": true,
  "texts": {
    "speed": {
      "key": "hud.machine_max.default_1.speed",
      "centered": true,
      "shadow": true,
      "scale": [2, 2, 2],
      "molang_args": [
        "math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)",
        "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
        "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))"
      ]
    },
    "gear": {
      "key": "hud.machine_max.default_1.gear",
      "centered": true,
      "shadow": true,
      "molang_args": ["vehicle.get('gear')??'NO GBX'"]
    }
  }
}
```

**HUD Function Overview:**

This HUD configuration implements the following core functions:

- **Speed Display**: Real-time display of vehicle speed in kilometers per hour, using three-digit display format
- **Gear Indicator**: Displays current transmission gear status, including forward gears, reverse gear, and neutral
- **Tachometer Pointer**: Controls dynamic rotation of tachometer pointer through animation system, reflecting engine operating status

##### Translation Key-Value Pair Analysis ([lang/en_us.json](../../lang/en_us.json))

The language file defines the translation format for HUD text. These translation keys work closely with text display in HUD configuration to achieve formatted display of dynamic content:

```json
"hud.machine_max.default_1.speed": "§o%1$s%2$s%3$s",
"hud.machine_max.default_1.gear": "§o%1$s",
"hud.machine_max.default_1.kmh": "§oKM/H"
```

**How Translation Keys Work:**

- **Speed Display** (`hud.machine_max.default_1.speed`): This translation key receives three parameters, corresponding to the hundreds, tens, and units digits of the speed. The `§o` code indicates using italic font for display, making speed readings more prominent.

- **Gear Display** (`hud.machine_max.default_1.gear`): Only needs one parameter to display current gear information, also using italic format to enhance readability.

**Complete Number Conversion Process:**

When the vehicle is moving, the HUD system processes speed signals in real time and displays them on the interface. This process can be divided into the following steps:

1. **Acquire Real-time Data**: First obtain current speed signal from the vehicle system, in meters per second
2. **Unit Conversion**: Convert meters per second to more commonly used kilometers per hour
3. **Number Decomposition**: Decompose the three-digit speed value into three independent digits: hundreds, tens, and units
4. **Format Display**: Fill the decomposed numbers into the placeholder positions of the translation key and apply italic format
5. **Final Presentation**: For example, when the vehicle is traveling at 123 km/h, the HUD will display as "§o123" in italic numbers

This design allows the HUD to reflect vehicle status in real time while maintaining interface aesthetics and consistency.

#### 2. Animation Control ([example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json))

Animation files define the dynamic behavior of HUD elements, such as the rotation effect of the tachometer pointer:

```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

This animation configuration implements the dynamic rotation effect of the tachometer pointer. When engine speed changes, the pointer rotates in real time, providing intuitive speed feedback to the driver.

#### 3. Subsystem Association ([senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json))

Seat subsystem configuration associates HUDs with specific seats and defines HUD display rules for different perspectives:

```json
{
  "views": {
    "enable_first_person": true,
    "first_person_hud": ["machine_max:example_hud"],
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_person_hud": ["machine_max:example_hud"],
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

**Flexibility of First-Person HUD**: You can add multiple HUD components to the first-person perspective as needed. For example, you can simultaneously display different HUD elements such as speedometer, tachometer, fuel gauge, etc., by simply adding the corresponding HUD resource paths to the `first_person_hud` array.

## Best Practices

1. **Performance Optimization**: Keep HUD models simple and avoid complex geometry to ensure smooth performance
2. **Consistent Design**: Maintain consistent visual style across different HUD elements
3. **Clear Information Hierarchy**: Prioritize important information and use appropriate sizing and positioning
4. **Responsive Design**: Ensure HUD elements work well in both first-person and third-person perspectives
5. **Testing**: Thoroughly test HUD functionality across different vehicles and scenarios
6. **Localization**: Use translation keys for all text to support multiple languages
7. **Error Handling**: Implement proper fallbacks for missing data or subsystem failures