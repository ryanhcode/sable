Sable 允许数据包（datapack）为维度指定自定义物理参数。这些配置从 `/data/<namespace>/dimension_physics/<name>.json` 加载。

### 字段

**`dimension`**（必填）：此配置所适用的维度的资源位置（resource location）。

**`priority`**（可选，默认 `1000`）：当多个配置针对同一维度时，优先级最高的配置生效。Sable 内置的默认值使用优先级 `0`，因此任何数据包配置都会自动覆盖它们。

**`base_gravity`**（可选，默认 `[0.0, -11.0, 0.0]`）：重力加速度，为三维向量，单位 m/秒²。默认值以 11 m/s² 向下拉扯。

**`base_pressure`**（可选，默认 `1.0`）：应用于维度中所有位置的压强倍率。设为 `0` 即为真空。如果同时定义了 `pressure_function`，两者会合并。

**`pressure_function`**（可选）：用于控制空气压强随海拔变化的贝塞尔曲线控制点列表。每个点包含 `altitude`（y 层高度）、`value`（该高度处的压强）和 `slope`（变化速率）。省略此字段则以 `base_pressure` 保持均匀压强。

**`universal_drag`**（可选，默认 `0.09`）：应用于维度中所有运动的平直阻力系数。

**`magnetic_north`**（可选，默认 `[0.0, 0.0, 0.0]`）：指向磁北的方向向量。`[0, 0, 0]` 表示不存在磁场。

### 示例

一个重力更低、无阻力、无气压的月球维度：
```js
// /data/examplemod/dimension_physics/moon.json
{
    "dimension": "examplemod:moon",

    // 默认优先级为 1000
    // 优先级更高的配置"胜出"
    "priority": 1000,

    // 将重力修改为较低值
    "base_gravity": [0.0, -4.0, 0.0],

    // 无气压
    "base_pressure": 0.0,

    // 无平直阻力
    "universal_drag": 0.0,

    // 无磁北
    "magnetic_north": [0.0, 0.0, 0.0]
}
```

### 内置默认值

Sable 会为原版维度生成这些配置。此处展示它们仅供参考，数值为近似值。
`pressure_function` 是一条近似指数衰减的曲线，以海平面为中心，在地下被钳制为至多 1.5，并在建造上限处有 40 米的平滑下落。

**主世界（Overworld）**：
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

**下界（Nether）**：
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

**末地（End）**：
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
