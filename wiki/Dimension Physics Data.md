Sable allows datapacks to specify custom physics parameters for dimensions. These are loaded from `/data/<namespace>/dimension_physics/<name>.json`.

### Fields

**`dimension`** (required): The resource location of the dimension this config applies to.

**`priority`** (optional, default `1000`): When multiple configs target the same dimension, the one with the highest priority wins. Sable's built-in defaults use priority `0`, so any datapack config overrides them automatically.

**`base_gravity`** (optional, default `[0.0, -11.0, 0.0]`): Gravitational acceleration as a 3D vector in m/sec². The default pulls straight down at 11 m/s².

**`base_pressure`** (optional, default `1.0`): The pressure multiplier applied everywhere in the dimension. Set to `0` for a vacuum. If `pressure_function` is also defined, the two combine.

**`pressure_function`** (optional): A list of bezier control points for controlling air pressure with altitude. Each point has `altitude` (y-level), `value` (pressure at that altitude), and `slope` (rate of change). Omit this field for uniform pressure at `base_pressure`.

**`universal_drag`** (optional, default `0.09`): A flat drag coefficient applied to all motion in the dimension.

**`magnetic_north`** (optional, default `[0.0, 0.0, 0.0]`): Direction vector pointing toward magnetic north. `[0, 0, 0]` means no magnetic field.

### Examples

A moon dimension with lower gravity, no drag, and no air pressure:
```js
// /data/examplemod/dimension_physics/moon.json
{
    "dimension": "examplemod:moon",
  
    // Default priority of 1000
    // Higher priority configs "win"
    "priority": 1000,
  
    // Modify the gravity to be low
    "base_gravity": [0.0, -4.0, 0.0],
    
    // No air pressure   
    "base_pressure": 0.0,
  
    // No universal drag
    "universal_drag": 0.0, 
    
    // No magnetic north
    "magnetic_north": [0.0, 0.0, 0.0]
}
```


### Built-in Defaults

Sable generates these configs for vanilla dimensions. They're shown here, with approximate values, for reference.
The `pressure_function` is a curve approximating an exponential decay, centered around sea level, clamped to at most 1.5 underground, with a 40-meter smooth drop-off at the build limit.

**Overworld**:
```json
{
    "dimension": "minecraft:overworld",
    "priority": 0,
    "universal_drag": 0.09,
    "base_gravity": [0.0, -11.0, 0.0],
    "base_pressure": 1.0,
    "pressure_function": [
        { "altitude": -38.366277, "value": 1.5,      "slope": -0.006    },
        { "altitude": 63.0,       "value": 1.0,      "slope": -0.004    },
        { "altitude": 263.0,      "value": 0.449329, "slope": -0.001797 },
        { "altitude": 280.0,      "value": 0.419786, "slope": -0.001679 },
        { "altitude": 320.0,      "value": 0.0,      "slope": -0.020989 }
    ],
    "magnetic_north": [0.0, 0.0, 0.0]
}
```

**Nether**:
```json
{
    "dimension": "minecraft:the_nether",
    "priority": 0,
    "universal_drag": 0.09,
    "base_gravity": [0.0, -11.0, 0.0],
    "base_pressure": 1.0,
    "pressure_function": [
        { "altitude": 0.0,   "value": 1.136553, "slope": -0.004546 },
        { "altitude": 32.0,  "value": 1.0,      "slope": -0.004    },
        { "altitude": 88.0,  "value": 0.799315, "slope": -0.003197 },
        { "altitude": 128.0, "value": 0.0,      "slope": -0.039966 }
    ],
    "magnetic_north": [0.0, 0.0, 0.0]
}
```

**End**:
```json
{
    "dimension": "minecraft:the_end",
    "priority": 0,
    "universal_drag": 0.09,
    "base_gravity": [0.0, -11.0, 0.0],
    "base_pressure": 1.0,
    "pressure_function": [
        { "altitude": 0.0,   "value": 1.0,      "slope": -0.004    },
        { "altitude": 200.0, "value": 0.449329, "slope": -0.001797 },
        { "altitude": 216.0, "value": 0.421473, "slope": -0.001686 },
        { "altitude": 256.0, "value": 0.0,      "slope": -0.021074 }
    ],
    "magnetic_north": [0.0, 0.0, 0.0]
}
```