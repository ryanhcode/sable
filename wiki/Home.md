## Depending on Sable
[![Sable 1.21.1](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.ryanhcode.dev%2Freleases%2Fdev%2Fryanhcode%2Fsable%2Fsable-common-1.21.1%2Fmaven-metadata.xml&label=Sable%201.21.1)]([-1.21.1/](https://maven.ryanhcode.dev/releases/dev/ryanhcode/sable/sable-common-1.21.1/))

Copy the following segments into your `build.gradle` file depending on the platform:

### NeoForge

<details>
  <summary>Click to expand</summary>

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
  <summary>Click to expand</summary>

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
  <summary>Click to expand</summary>

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

### Working with Sable

- Simple compatability with [Sable Companion](https://github.com/ryanhcode/sable-companion)
- [Working with Entities](https://github.com/ryanhcode/sable/wiki/Working-With-Entities)
- [Block Physics Properties](https://github.com/ryanhcode/sable/wiki/Block-Physics-Properties)
- [Dimension Physics Data](https://github.com/ryanhcode/sable/wiki/Dimension-Physics-Data)