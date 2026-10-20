# Application Guide for JSON Schema in Machine-Max Content Pack Creation

## What is JSON Schema?

JSON Schema is a specification used to describe and validate the structure of JSON documents. For Machine-Max content pack creation, JSON Schema can help you:

- **Validate configuration format**: Ensure your JSON configuration files have the correct format
- **Provide intelligent suggestions**: Display attribute descriptions and type requirements in supported editors
- **Reduce errors**: Identify configuration errors early, avoiding issues in-game

## Why Should Content Pack Creators Care About JSON Schema?

Even if you have no programming experience, JSON Schema can make your content pack creation process smoother:

1. **Auto-completion**: Modern editors (like VSCode) can provide attribute name auto-completion based on Schema
2. **Error notifications**: Get immediate notifications of configuration errors instead of discovering them only when in-game
3. **Attribute descriptions**: View specific meanings and usage methods of each configuration item
4. **Type checking**: Ensure your input data types are correct (numbers, strings, booleans, etc.)

## How to Use JSON Schema?

### 1. Configure Editor Support

Add a `$schema` field at the top of your JSON file, for example:

```json
{
  "$schema": "./docs/en_us/schemas/part_definition_schema.json",
  "vehicle_durability_rate": 1.0,
  "variants": {
    "default": {
      "icon": "machine_max:textures/icon/example.png",
      "models": "machine_max:example.geo"
    }
  }
}
```

### 2. Recommended Editors

- **VSCode**: Install the "JSON" extension, which automatically supports JSON Schema
- **Sublime Text**: Install relevant plugins to support Schema
- **Atom**: Use relevant packages to provide Schema support

### 3. Enable JSON Schema Validation in VSCode

1. Open VSCode settings (Ctrl/Cmd + ,)
2. Search for "json schemas"
3. Add the Machine-Max Schema path
4. Or directly use the `$schema` field in the JSON file

## Machine-Max Schema Structure

### Part Definition Schema (part_definition_schema.json)

This is the main Schema for defining vehicle parts, containing the following key fields:

- `vehicle_durability_rate`: Vehicle durability contribution coefficient (number, between 0-1)
- `vehicle_damage_rate`: Normal vehicle damage transmission coefficient (number)
- `functional_threshold`: Part functional threshold (number, between 0-1, default `0.5`)
- `share_durability`: Whether parts within the component share durability (boolean)
- `variants`: List of part variants (object)

### Sub-part Schema

In the `schemas/part/` directory, detailed definitions for various sub-parts are included:

- `variant_attr.schema.json`: Variant attribute definitions
- `subpart/`: Sub-part related Schema
  - `sub_part_attr.schema.json`: Sub-part general attributes
  - `hit_box_attr.schema.json`: Hit box attributes
  - `interact_box_attr.schema.json`: Interaction area attributes
  - `connector/`: Connector related attributes

### Subsystem Schema

In the `schemas/subsystem/` directory, configuration structures for vehicle subsystems (such as engine, transmission, etc.) are defined.

## Practical Application Examples

### Step 1: Create New Part

1. Copy an existing part JSON file as a template
2. Add `$schema` reference at the beginning of the file
3. Add configuration items according to the editor's intelligent suggestions

### Step 2: Validate Configuration

1. Check if the editor displays errors after saving the file
2. Correct configuration format issues according to the prompts
3. Test if the content pack works properly in-game

### Step 3: Debug Issues

If the content pack doesn't work in-game, check:

1. Whether the JSON format is correct (using Schema validation)
2. Whether all required fields are filled in
3. Whether data types match (number vs string, etc.)

## Common Issues and Solutions

### Q: Editor shows errors, but the configuration looks correct?
A: Check:
- Whether JSON syntax is correct (commas, brackets, etc.)
- Whether field names are spelled correctly
- Whether data types match (e.g., requiring a number but inputting a string)

### Q: How do I know which fields are required?
A: Check the `required` field in the Schema file, or there will be corresponding suggestions in supported editors.

### Q: How do I find the correct resource path?
A: Schema files usually include descriptions of path formats, such as `namespace:path` format.

## Efficiency Tips

1. **Use templates**: Create new configurations based on existing working configurations
2. **Add incrementally**: First add basic configurations, verify they work, then add advanced options
3. **Refer to examples**: Check example configuration files in the official pack
4. **Real-time validation**: View validation results in the editor in real-time

## Summary

JSON Schema is a powerful auxiliary tool for content pack creation, helping you create correctly formatted and structurally complete configuration files. Even without programming experience, using Schema-supported editors can significantly improve production efficiency and accuracy.

When starting out, it's recommended to begin with simple configurations and gradually familiarize yourself with the various functions provided by Schema. As your experience accumulates, you'll find that creating content packs becomes simpler and more reliable.