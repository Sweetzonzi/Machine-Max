# Part Definition JSON Documentation

## Overview

Part definition JSON files are used to describe the attributes of various parts in the vehicle system. Each part consists of multiple sub-parts, and each sub-part can contain functional modules such as connectors and subsystems.

## Basic Structure of Parts

```json
{
  "Part Name": {
    "variants": {
      "Variant Name": {
        "icon": "texture path",
        "tags": ["tag list"],
        "models": {
          "state": "model path"
        },
        "textures": {
          "state": ["texture path list"]
        },
        "animations": {
          "state": "animation path"
        },
        "sub_parts": {
          "Sub-part Name": {
            // Sub-part attributes
          }
        }
      }
    }
  }
}
```

## PartType and VariantAttr

### PartType
PartType defines the basic attributes of a part, including:
- **name**: Part name
- **vehicleDurabilityRate**: Vehicle durability contribution coefficient
- **vehicleDamageRate**: Vehicle damage transfer coefficient
- **vehicleDamageRateDestroyed**: Damage transfer coefficient when part is destroyed
- **shareDurability**: Whether parts share durability within the component
- **variants**: List of all variants of the part

### VariantAttr
VariantAttr defines the specific appearance and functionality of a particular variant of the part:
- **icon**: Icon path
- **tags**: Part tag list
- **models**: Mapping of states to model paths
- **textures**: Mapping of states to texture path lists
- **animations**: Mapping of states to animation paths
- **subParts**: Mapping of sub-part names to sub-part attributes

## SubPartAttr

Sub-parts are the basic physical units that make up a part, having independent mass, collision, and physical characteristics.

```json
"Sub-part Name": {
  "start_bone": "starting bone name",
  "end_bones": ["ending bone name list"],
  "durability": 20.0,
  "mass": 25.0,
  "projected_area": [0.0, 0.0, 0.0],
  "block_collision": "true",
  "collision_height": -1.0,
  "climb_assist": false,
  "hit_boxes": {
    "Hit Box Name": {
      // Hit box attributes
    }
  },
  "interact_boxes": {
    "Interaction Box Name": {
      // Interaction box attributes
    }
  },
  "connectors": {
    "Connector Name": {
      // Connector attributes
    }
  },
  "subsystems": {
    "Subsystem Name": {
      // Subsystem attributes
    }
  }
}
```

## Connector

Connectors are nodes for connecting parts, divided into two types:
- **Special**: Active connection port
- **AttachPoint**: Passive connection port

```json
"Connector Name": {
  "locator": "locator name",
  "type": "connector type",
  "integrity": 10.0,
  "required_tags": [],
  "acceptable_tags": [],
  "forbidden_tags": [],
  "joint_attrs": {
    // Joint attributes
  },
  "signal_translations": {},
  "signal_targets": {},
  "collide_between_parts": false,
  "breakable": true,
  "connected_to": "connection target"
}
```

## Subsystem

Subsystems provide specific functions to parts, such as seats, engines, transmission systems, etc.

```json
"Subsystem Name": {
  "type": "subsystem type",
  "model": "subsystem model",
  // Other attributes specific to the subsystem
}
```

## Notes

1. Detailed configuration of Connectors and Subsystems will be described in separate documents
2. All paths are relative to the resource pack root directory
3. Coordinate system follows the right-hand rule with Y-axis pointing upward
4. Angle units are generally in degrees unless otherwise specified

---
*This document provides an overview of Machine Max vehicle system part definition JSON. For detailed technical specifications, please refer to the relevant API documentation.*