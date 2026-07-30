# ZH-TW Auto Pack (Fabric 1.20.1)

Client-side mod that creates and enables `resourcepacks/ZH-TW Auto Pack` when Minecraft starts.
It scans installed mod jars, zip/folder resource packs, and translates every discovered
`assets/<namespace>/lang/zh_cn.json` to `zh_tw.json` with OpenCC — except namespaces that
already supply their own `zh_tw.json`.

The generated pack is then added to Minecraft's selected resource packs and resources are
reloaded. It is safe to regenerate: existing output files are overwritten, while originals
are never modified.

## Install

1. Install Fabric Loader `0.15.11` (or newer) and Fabric API for Minecraft 1.20.1.
2. Put `zhtw-autopack-fabric-1.0.0.jar` in `.minecraft/mods`.
3. Start the game. The generated pack appears as **ZH-TW Auto Pack**.

## Build

Requires JDK 17:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew build
```

The distributable jar is under `build/libs/`.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
