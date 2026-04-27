<p align="center"><img src="./.idea/icon.png" alt="Logo" width="200"></p>
<h1 align="center">Sable<br>
<div align="center">
   <a href="https://discord.gg/createaeronautics">
        <img alt="Discord" src="https://img.shields.io/discord/937435293294919690?style=flat&logo=discord&label=Discord&color=5865F2">
    </a>
    <a href="https://modrinth.com/mod/sable">
        <img src="https://img.shields.io/modrinth/dt/sable?logo=modrinth&amp;label=&amp;suffix=%20&amp;style=flat&amp;color=242629&amp;labelColor=5CA424&amp;logoColor=1C1C1C" alt="Modrinth Download"/>
    </a>
</div>
</h1>

<p>Sable is an intrusive library mod for Minecraft that adds my take on interactive moving block structures, called "sub-levels". Sub-levels contain normal Minecraft chunks, entities, and block-entities, but exist at a separate dynamic position and orientation within Minecraft levels. My goal is to maximize compatibility, performance, and immersion of interacting with sub-levels, as simply as possible.</p>

### Compatibility Warning

Sable is an incredibly intrusive mod. It makes extensive use of mixins, and is prone to extensive compatibility issues
with other mods.

### Developers

For adding optional, and simple compatibility to a mod to function alongside Sable,
view [Sable Companion](https://github.com/ryanhcode/sable-companion).

View the [Sable Developer Wiki](https://github.com/ryanhcode/sable/wiki) for documentation and guides.

# Building Rust Natives

1. Install Docker from https://www.docker.com/get-started/ or from your relevant package manager
2. Run `gradlew common:buildImages` (only has to be done once)
3. Run `gradlew common:buildRustNatives`

### Thanks

- Dimforge maintainers and contributors for their amazing work on the Rapier physics engine, included in the default
  physics pipeline
- Eriksonn for the sub-level splitting region algorithm, floating blocks, and amazing math wizardry
- Ocelot for the fancy sub-level renderer and an incredible amount of optimization and API help
- Cyvack for many Create compatibility fixes and features, assembly help, and general development
- BeeIsYou for lift math, compatibility fixes, bug-fixes, and lots of general development
- KyanBirb for assets, compatibility fixes, bug-fixes and lots of general development
- Cake for bug-fixes and general development help
- Rhyguy1 for morale

### Licensing

Unless otherwise stated, all content in this repository is licensed under [Polyform Shield License 1.0.0](LICENSE.md) by
RyanHCode.
