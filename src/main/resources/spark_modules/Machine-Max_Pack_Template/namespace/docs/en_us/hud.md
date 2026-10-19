# HUD Development Guide

Machine-Max provides a powerful custom HUD system that allows creating dedicated interface displays for different vehicles and subsystems. The HUD system is based on models, animations, and Molang scripts, enabling dynamic dashboards, status indicators, and information displays. HUDs have three layers: a **style** describes what it looks like, a **layout** is an ordered list of elements, and an **element** places one style at one specific position.

## HUD System Overview

### Core Component File Paths

Here are the core component file paths for the example HUD, where you can find complete implementation examples:

- **HUD Style**: [huds/example_hud.json](../../huds/example_hud.json)
- **HUD Layout**: [hud_layouts/seat_dashboard.json](../../hud_layouts/seat_dashboard.json)
- **3D Model File**: [models/hud/example_hud.geo.json](../../models/hud/example_hud.geo.json)
- **Animation File**: [animations/hud/example_hud/example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json)
- **Texture File**: [textures/hud/example_hud.png](../../textures/hud/example_hud.png)
- **Language File**: [lang/en_us.json](../../lang/en_us.json)
- **Subsystem Configuration**: [subsystems/default_seat.json](../../subsystems/default_seat.json)
- **Advanced Subsystem Configuration**: [subsystems/senna_gtr/senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json)

## Table of Contents
- [HUD System Overview](#hud-system-overview)
- [HUD Styles (huds/)](#hud-styles-huds)
- [HUD Layouts (hud_layouts/)](#hud-layouts-hud_layouts)
- [Model and Animation Requirements](#model-and-animation-requirements)
- [Text Display Configuration](#text-display-configuration)
- [Molang in HUD Applications](#molang-in-hud-applications)
- [Subsystem HUD Association](#subsystem-hud-association)
- [Practical Case Analysis](#practical-case-analysis)
- [Best Practices](#best-practices)

## HUD System Overview

Machine-Max's HUD system consists of the following core components:

1. **HUD Style** (`huds/*.json`) - Defines model, animation, texture, tint, and text slots
2. **HUD Layout** (`hud_layouts/*.json`) - An ordered element array deciding which styles are drawn and where
3. **3D Model** (`.geo.json` file) - Provides HUD geometry and bone structure
4. **Animation File** (`.animation.json` file) - Controls dynamic behavior of HUD elements
5. **Texture File** (`.png` file) - Provides visual appearance of the HUD
6. **Subsystem Configuration** - Mounts a layout on a seat or sight through a `*_hud_layout` field

## HUD Styles (huds/)

A HUD style defines the internal appearance of one HUD and contains no position, rotation, scale, or projection.

### Basic Structure
```json
{
  "type": "machine_max:model_hud",
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "color": [255, 255, 255],
  "alpha": 255,
  "texts": {
    // Text configuration
  },
  "scissor": {
    "enable": false,
    "x": 0,
    "y": 0,
    "width": 0,
    "height": 0
  }
}
```

### Configuration Parameter Description

#### Basic Properties
- **type** (Required): Carrier type: `machine_max:model_hud` (Bedrock model) or `machine_max:aui_hud` (AUI page)
- **model** (Required): HUD 3D model resource address, e.g., `machine_max:example_hud.geo`
- **animation** (Required): HUD animation resource address, e.g., `machine_max:example_hud`
- **texture** (Required): HUD texture resource address, e.g., `machine_max:textures/hud/example_hud.png`
- **color** (Optional): RGB color, default white
- **alpha** (Optional): Transparency, default 255

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
- **scissor**: Clipping rectangle configuration (optional, disabled by default)
  - **enable**: Whether to enable clipping rectangle
  - **x**: Clipping rectangle center x coordinate (pixels)
  - **y**: Clipping rectangle center y coordinate (pixels)
  - **width**: Clipping rectangle width (pixels)
  - **height**: Clipping rectangle height (pixels)

### AUI Page Styles

`machine_max:aui_hud` describes a HUD with an HTML page supplied by the pack's `documents/` directory. The page can render in the GUI/HUD orthographic overlay or on a perspective plane in camera space.

```json
{
  "type": "machine_max:aui_hud",
  "page": "machine_max/weapon_hud_element/weapon_ammo.html",
  "defaults": { "--hud-accent": "#7dd3fc" }
}
```

- **page** (Required): AUI page key in the form `<pack namespace>/<relative path>`; the pack file `documents/weapon_hud_element/weapon_ammo.html` maps to `machine_max/weapon_hud_element/weapon_ammo.html`
- **defaults** (Optional): CSS variables written onto the page `body`, readable through `var(...)` in the page stylesheet

The page shrinks to its content: the runtime adds the `mm-hud` class to `body` and sets inline rules for `inline-block`, zero margin and a transparent background. The page describes the component content; its layout controls placement and projection. Put a dark background on the component container when needed. The layout field `perspective: false` selects the orthographic overlay and `perspective: true` selects a perspective plane. The field defaults to `true`, so screen-anchored AUI elements must explicitly set it to `false`.

AUI HUD pages receive no mouse input. Perspective rendering supports page geometry, text and clipping; complex screen-sampling effects such as `backdrop-filter` are not guaranteed.

## HUD Layouts (hud_layouts/)

A layout file is an array. Each entry is an element: one style reference plus a complete set of placement values. Elements are inline in the layout and are not registered separately; the same style may appear multiple times in one layout.

```json
[
  {
    "style": "machine_max:example_hud",
    "perspective": true,
    "offset": [95, 55, -135],
    "rotation": [-25, -30, 0],
    "scale": [15, 15, 15]
  }
]
```

| Field | Required | Description |
| --- | --- | --- |
| `style` | Yes | Style resource key pointing at a file under `huds/` |
| `perspective` | No | Default `true`; `true` uses perspective projection, `false` uses orthographic projection |
| `offset` | No | Perspective: camera-space translation `(x, -y, z)`; a visible plane must be in front of the camera (normally `z < 0`). Orthographic model: pixel offset from screen center. Orthographic AUI: GUI pixel offset from the aligned `anchor` position, with `z` controlling paint depth |
| `rotation` | No | `[x, y, z]` rotation angles in degrees; perspective AUI composes Z/Y/X rotations, while orthographic AUI ignores this field |
| `scale` | No | Default `[1, 1, 1]`; orthographic AUI scales its two page axes independently with X/Y, while perspective AUI converts CSS pixels into camera-space units |
| `scope_behavior` | No | Default `follow_position`; only effective in a sight layout: `screen_fixed` / `follow_position` / `follow_transform` |
| `ignore_zoom` | No | Default `false`; when `true`, element size does not scale with sight zoom |
| `anchor` | No | Orthographic AUI only: default `top_left`, one of `top_center`, `top_right`, `center_left`, `center`, `center_right`, `bottom_left`, `bottom_center`, `bottom_right`; the element's matching corner (or center) aligns with the screen's matching corner (or center). Models and perspective AUI ignore this field |

Orthographic AUI computes `anchor` alignment from the content size, then adds `offset.x/y`. The AUI viewport's CSS-to-GUI conversion factor applies equally to both axes, followed by the independent `scale.x/y` factors; for example, displayed width is `CSS width × viewport.renderScale × scale.x`. Painting and clipping share the same position and scale. The layout fields `rotation`, `scope_behavior` and `ignore_zoom` do not affect orthographic AUI; CSS can rotate content inside the page.

Perspective AUI uses the page content center as its local origin and flips the downward CSS Y axis into the upward camera Y axis. Its placement matrix is `T(x, -y, z) × Rz × Ry × Rx × S(scale.x, -scale.y, scale.z)`, with rotation angles in degrees. This mode converts CSS pixels directly into camera-space units through `scale`, without the orthographic viewport's CSS-to-GUI conversion, and does not use a screen `anchor`. In a sight view, `scope_behavior` and `ignore_zoom` participate in placement; `screen_fixed` uses the camera-space origin corresponding to screen center.

Save the style example in “AUI Page Styles” as `huds/aui_weapon_status.json` in the `machine_max` namespace to use this layout for a centered orthographic panel and a perspective panel in the upper right. The perspective values assume content of about `280 × 120` CSS pixels, a 70° field of view and a 16:9 frame; adjust placement for the actual content size and field of view.

```json
[
  {
    "style": "machine_max:aui_weapon_status",
    "perspective": false,
    "anchor": "center",
    "offset": [0, 0, 0],
    "scale": [1, 1, 1]
  },
  {
    "style": "machine_max:aui_weapon_status",
    "perspective": true,
    "scope_behavior": "screen_fixed",
    "offset": [95, -60, -135],
    "rotation": [15, -25, -5],
    "scale": [0.2, 0.2, 0.2]
  }
]
```

Key rule: the file path relative to the module directory without `.json`, prefixed by the pack namespace, is the resource key. `hud_layouts/seat_dashboard.json` is `machine_max:seat_dashboard`; `hud_layouts/foo/bar.json` is `machine_max:foo/bar`. Styles follow the same rule: `huds/example_hud.json` is `machine_max:example_hud`. Layouts and styles live in separate modules, so identical keys do not collide.

Layout array order is the submission order within one projection mode; the order between perspective and orthographic batches is decided by the render batches, not by the layout.

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
      "0.0": [0, 0, "240*(math.abs(global.get('engine_speed')??0)*30/math.pi)/9000"]
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
    "math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)",
    "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
    "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))"
  ],
  "significand": 0
}
```

### Gear Display
```json
"gear": {
  "key": "hud.machine_max.default_1.gear",
  "molang_args": ["global.get('gear')??'NO GBX'"],
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
math.abs(global.get('vehicle_speed')??0.0) * 3.6

// Get engine speed and convert to RPM
math.abs(global.get('engine_speed')??0) * 30 / math.pi
```

#### Number Decomposition Display
```molang
// Hundreds digit
math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)

// Tens digit
math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)

// Units digit
math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))
```

#### Conditional Display
```molang
// Display warnings based on subsystem status
local.has_subsystem('engine') && local.subsystem_destroyed('engine') ? 1.0 : 0.0
```

## Subsystem HUD Association

HUDs are associated with a specific seat or sight through the host's view fields: seats use `first_hud_layout` / `third_hud_layout`, cameras and sights use `scope_hud_layout`. Each field is a layout resource key; an omitted field means that view has no HUD.

### Seat Subsystem Configuration
```json
{
  "type": "machine_max:seat",
  "views": {
    "enable_first_person": true,
    "first_hud_layout": "machine_max:seat_dashboard",
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_hud_layout": "machine_max:seat_dashboard",
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

### Sight/Camera Subsystem Configuration
```json
{
  "type": "machine_max:sight",
  "scope_hud_layout": "machine_max:scope_gunner"
}
```

### View Property Description
- **enable_first_person**: Whether to enable first-person perspective
- **first_hud_layout**: Layout used by the first-person view; omitted means no HUD in that view
- **first_person_offset**: First-person perspective offset (x+right, y+up, z+back, in meters)
- **enable_third_person**: Whether to enable third-person perspective
- **third_hud_layout**: Layout used by the third-person view; omitted means no HUD in that view
- **follow_vehicle**: Whether camera follows vehicle rotation
- **focus_on_center**: Whether camera focuses on vehicle center
- **distance_scale**: Third-person perspective distance multiplier
- **scope_hud_layout** (camera/sight): Layout used by the sight view; omitted means the sight adds no HUD from a layout

## Practical Case Analysis

### Example HUD Analysis
Using `machine_max:example_hud` as an example, analyze its complete implementation. You can view the complete implementation files through the following paths:

#### 1. HUD Style ([example_hud.json](../../huds/example_hud.json))

This is a fully functional vehicle dashboard style that can simultaneously display key information such as engine speed, transmission gear, and vehicle speed. Placement lives in [seat_dashboard.json](../../hud_layouts/seat_dashboard.json).

```json
{
  "type": "machine_max:model_hud",
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "texts": {
    "speed": {
      "key": "hud.machine_max.default_1.speed",
      "centered": true,
      "shadow": true,
      "scale": [2, 2, 2],
      "molang_args": [
        "math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)",
        "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
        "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))"
      ]
    },
    "gear": {
      "key": "hud.machine_max.default_1.gear",
      "centered": true,
      "shadow": true,
      "molang_args": ["global.get('gear')??'NO GBX'"]
    }
  }
}
```

**HUD Function Overview:**

This HUD style and the layout it references implement the following core functions:

- **Speed Display**: Real-time display of vehicle speed in kilometers per hour, using three-digit display format
- **Gear Indicator**: Displays current transmission gear status, including forward gears, reverse gear, and neutral
- **Tachometer Pointer**: Controls dynamic rotation of tachometer pointer through animation system, reflecting engine operating status

##### Translation Key-Value Pair Analysis ([lang/en_us.json](../../lang/en_us.json))

The language file defines the translation format for HUD text. These translation keys work closely with the text slots in the style to achieve formatted display of dynamic content:

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
      "0.0": [0, 0, "240*(math.abs(global.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

This animation configuration implements the dynamic rotation effect of the tachometer pointer. When engine speed changes, the pointer rotates in real time, providing intuitive speed feedback to the driver.

#### 3. Subsystem Association ([senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json))

Seat subsystem configuration associates HUDs with specific seats and defines the layout used by each view:

```json
{
  "views": {
    "enable_first_person": true,
    "first_hud_layout": "machine_max:seat_dashboard",
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_hud_layout": "machine_max:seat_dashboard",
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

**One layout can hold multiple elements**: to show a speedometer, tachometer, and fuel gauge together, write multiple elements in the layout array, each referencing its style and giving its own placement; a view reads a single `*_hud_layout` field. When a different position is needed, copy the layout and change its placement rather than duplicating the style.

## Best Practices

1. **Performance Optimization**: Keep HUD models simple and avoid complex geometry to ensure smooth performance
2. **Consistent Design**: Maintain consistent visual style across different HUD elements
3. **Clear Information Hierarchy**: Prioritize important information and use appropriate sizing and positioning
4. **Responsive Design**: Ensure HUD elements work well in both first-person and third-person perspectives
5. **Testing**: Thoroughly test HUD functionality across different vehicles and scenarios
6. **Localization**: Use translation keys for all text to support multiple languages
7. **Error Handling**: Implement proper fallbacks for missing data or subsystem failures
