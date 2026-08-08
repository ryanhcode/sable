## 依赖 Sable
[![Sable 1.21.1](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.ryanhcode.dev%2Freleases%2Fdev%2Fryanhcode%2Fsable%2Fsable-common-1.21.1%2Fmaven-metadata.xml&label=Sable%201.21.1)](https://maven.ryanhcode.dev/releases/dev/ryanhcode/sable/sable-common-1.21.1/)

根据你的平台，将以下片段复制到你的 `build.gradle` 文件中：

### NeoForge

<details>
  <summary>点击展开</summary>

```groovy
repositories {
    exclusiveContent { // Sable
        forRepository {
            maven {
                url = "https://maven.ryanhcode.dev/releases"
                name = "RyanHCode Maven"
            }
        }
        filter {
            includeGroup("dev.ryanhcode.sable")
            includeGroup("dev.ryanhcode.sable-companion")
        }
    }
}

dependencies {
    api("dev.ryanhcode.sable:sable-common-${project.minecraft_version}:${project.sable_version}")
}
```

</details>

### Fabric

<details>
  <summary>点击展开</summary>

```groovy
repositories {
    exclusiveContent { // Sable
        forRepository {
            maven {
                url = "https://maven.ryanhcode.dev/releases"
                name = "RyanHCode Maven"
            }
        }
        filter {
            includeGroup("dev.ryanhcode.sable")
            includeGroup("dev.ryanhcode.sable-companion")
        }
    }
}

dependencies {
    modApi("dev.ryanhcode.sable:sable-fabric-${project.minecraft_version}:${project.sable_version}")
}
```

</details>

### Common

<details>
  <summary>点击展开</summary>

```groovy
repositories {
    exclusiveContent { // Sable
        forRepository {
            maven {
                url = "https://maven.ryanhcode.dev/releases"
                name = "RyanHCode Maven"
            }
        }
        filter {
            includeGroup("dev.ryanhcode.sable")
            includeGroup("dev.ryanhcode.sable-companion")
        }
    }
}

dependencies {
    api "dev.ryanhcode.sable:sable-common-${project.minecraft_version}:${project.sable_version}"
}
```

</details>

### 使用 Sable

- 通过 [Sable Companion](https://github.com/ryanhcode/sable-companion) 实现简单兼容
- [与实体一起工作](https://github.com/ryanhcode/sable/wiki/Working-With-Entities)
- [方块物理属性](https://github.com/ryanhcode/sable/wiki/Block-Physics-Properties)
- [维度物理数据](https://github.com/ryanhcode/sable/wiki/Dimension-Physics-Data)
