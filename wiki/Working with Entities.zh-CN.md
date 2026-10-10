## "实体踢出"（Entity Kicking）

默认情况下，Sable 会将生成在子层级（sub-level）区域（plot）内的所有实体"踢出"到全局空间。
该操作会把实体传送到其全局位置，应用来自子层级的速度，并将实体的速度和旋转从子层级坐标系中转换出来。

对于某些实体，例如画或盔甲架，这种行为并不理想，预期的结果应是让实体留在子层级内部。
因此，Sable 提供了用于自定义实体与实体踢出交互方式的标签：

- `#sable:retain_in_sub_level` - 绝不将该实体从子层级中踢出。（例如：盔甲架、画）
- `#sable:destroy_when_leaving_plot` - 当该实体位于子层级区域内、但超出了包含子层级方块的边界时，销毁该实体。
- `#sable:destroy_with_sub_level` - 当包含该实体的子层级区域被销毁时，销毁该实体，而不是将其踢到全局世界。（例如：来自 Create 的超级胶水 Super Glue）

### 示例

要指定一个实体应留在子层级区域内、并且绝不被踢出：
```js
// /data/sable/tags/entity_type/retain_in_sub_level.json
{
  "replace": false,
  "values": [
    "examplemod:example_entity"
  ]
}
```

## 追踪（Tracking）
实体可以位于子层级的区域*之外*，但仍随子层级一起移动（例如：站在子层级上的玩家，或子层级围栏里的牛）。当实体站在子层级上时，Sable 会将其标记为"追踪"该子层级。

正在追踪某个子层级的实体：
- 会相对于该子层级进行网络同步
- 会相对于该子层级进行插值
- 会随着子层级旋转和平移而一起移动

正在追踪某个子层级的玩家，还会通过追踪点（tracking points）系统，以相对于子层级的位置进行登出和登入。

Sable 提供了用于检查实体的追踪子层级的工具：
```java
Entity entity = ...;
SubLevel subLevel = EntitySubLevelUtil.getTrackingSubLevel(this.entity);
```
