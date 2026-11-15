<p align="center"><img src="./.idea/icon.png" alt="Logo" width="200"></p>
<h1 align="center">Sable<br>
<div align="center">
   <a href="https://discord.gg/createaeronautics">
        <img alt="Discord" src="https://img.shields.io/discord/937435293294919690?style=flat&logo=discord&label=Discord&color=5865F2">
    </a>
    <a href="https://modrinth.com/mod/sable">
        <img src="https://img.shields.io/modrinth/dt/sable?logo=modrinth&amp;label=&amp;suffix=%20&amp;style=flat&amp;color=242629&amp;labelColor=5CA424&amp;logoColor=1C1C1C" alt="Modrinth Download"/>
    </a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/sable">
        <img src="https://img.shields.io/curseforge/dt/1312371?logo=curseforge&amp;label=&amp;suffix=%20&amp;style=flat&amp;color=242629&amp;labelColor=F16436&amp;logoColor=1C1C1C" alt="CurseForge Download"/>
    </a>
</div>
</h1>

<p>Sable 是一个面向 Minecraft 的侵入式库 Mod，实现了我所设想的交互式移动方块结构，称为"子层级"（sub-levels）。子层级内包含普通的 Minecraft 区块、实体和方块实体，但存在于 Minecraft 世界中一个独立动态位置和朝向上。我的目标是尽可能最大化与子层级交互时的兼容性、性能与沉浸感，并且尽量简单。</p>

### 兼容性警告

Sable 是一个侵入性极强的 Mod。它大量使用 mixin，极易与其他 Mod 产生兼容性问题。

### 开发者

如需添加可选且简单的兼容性，使某个 Mod 能与 Sable 协同工作，请查看 [Sable Companion](https://github.com/ryanhcode/sable-companion)。

查看 [Sable 开发者 Wiki](https://github.com/ryanhcode/sable/wiki) 获取文档和指南。

加入 sable zone 进行开发讨论：https://discord.gg/pnkzu2dtVA

# 构建 Rust Natives

1. 从 https://www.docker.com/get-started/ 或你的软件包管理器安装 Docker
2. 运行 `gradlew common:buildImages`（只需执行一次）
3. 运行 `gradlew common:buildRustNatives`

### 致谢

- Dimforge 的维护者与贡献者，感谢他们在默认物理管线中所包含的出色的 Rapier 物理引擎
- Eriksonn，感谢他的子层级拆分区域算法、悬浮方块，以及惊人的数学魔法
- Ocelot，感谢他出色的子层级渲染器，以及大量优化和 API 帮助
- Cyvack，感谢他为 Create 兼容性所做的许多修复和功能、装配帮助，以及整体开发
- BeeIsYou，感谢他的升力数学、兼容性修复、Bug 修复和大量整体开发
- KyanBirb，感谢他的资源、兼容性修复、Bug 修复和大量整体开发
- Cake，感谢他的 Bug 修复和整体开发帮助
- Rhyguy1，感谢他带来的士气

### 许可

除非另有说明，本仓库中的全部内容均受 RyanHCode 的 [Polyform Shield License 1.0.0](LICENSE.md) 许可。
