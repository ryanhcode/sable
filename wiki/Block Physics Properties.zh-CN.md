Sable 按方块状态（block-state）存储与物理相关的属性。
这些属性通过数据包（datapack）中的定义 JSON 进行配置。

### 可用属性

默认可用的属性包括：
- `sable:mass` - 方块的质量，单位 `kpg`。默认 `1.0`
- `sable:inertia` - 方块沿各轴的惯性（转动惯量）倍率，单位 `kpg*m^2`。使用前会乘以方块的质量。默认 `[1/6, 1/6, 1/6]`
- `sable:volume` - 方块的体积，单位 `m^3`。用于浮力计算。默认 `1.0`
- `sable:restitution` - 方块的弹性，范围 0-1。默认 `0.0`
- `sable:friction` - 方块的摩擦倍率。默认 `1.0`
- `sable:fragile` - 方块在受到撞击时是否应该破碎。默认 `false`
- `sable:floating_material` - 要指定的悬浮方块材质。默认 `null`
- `sable:floating_scale` - 悬浮方块材质的倍率。默认 `1.0`

### JSON 结构

方块物理属性定义 JSON 可以放在任意数据包（datapack）的 `physics_block_properties` 文件夹下。

```js
// /data/examplemod/physics_block_properties/example_block.json
{
    // 选择器可以是标签（tag），也可以是方块 ID。
    // 如果使用标签，标签中的所有方块都会受到影响。
    // 例如 `#examplemod:example_blocks` 或 `examplemod:example_block`
    "selector": "examplemod:example_block"

    // 优先级默认是 1000。
    // 定义按照优先级升序应用
    "priority": 1001,

    "properties": {
        // 任何属性都可以在此定义
        "sable:mass": 2.0
    },

    "overrides": {
        // 覆盖键是方块状态条件
        "lit=true": {
            // 任何属性都可以在此定义
            // 所有满足条件的方块状态都会受到影响
            "sable:mass": 3.0
        }
    }

}
```

### 示例

一个会弹跳的方块：

```js
// /data/examplemod/physics_block_properties/bouncy_block.json
{
  "selector": "examplemod:bouncy_block",

  "properties": {
    "sable:restitution": 0.5
  }
}
```

一个在伸出时没那么重的活塞：

```js
// /data/examplemod/physics_block_properties/piston.json
{
  "selector": "examplemod:piston",

  "properties": {
    "sable:mass": 1.0
  },

  "overrides": {
    "extended=true": {
      "sable:mass": 0.5
    }
  }
}
```

### 标签（Tags）

Sable 在其内置的数据包中包含了许多常用的物理方块属性标签。
如果你的方块不需要自定义属性定义，建议将方块放入预定义的标签中：

- `#sable:super_light` 质量 = 0.25
- `#sable:light` 质量 = 0.5
- `#sable:heavy` 质量 = 2.0
- `#sable:super_heavy` 质量 = 4.0

- `#sable:half_volume` 体积 = 0.5
- `#sable:quarter_volume` 体积 = 0.25

- `#sable:slippery` 摩擦 = 0.0
- `#sable:bouncy` 弹性 = 0.5
