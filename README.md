# Traditional Chinese Bridge (Minecraft 1.20.1)

Client-side mod that creates and enables `resourcepacks/TChineseB-1.20.1.zip` when Minecraft starts.
It scans installed mod jars, zip/folder resource packs, and translates every discovered
`assets/<namespace>/lang/zh_cn.json` to `zh_tw.json` with OpenCC — except namespaces that
already supply their own `zh_tw.json`.

Simplified Chinese files from all eligible installed mods and resource-pack zip/folders are
merged into that single generated zip. Sources that already contain their own `zh_tw.json`
are left alone. The generated pack is then added to Minecraft's selected resource packs and resources are
reloaded. It is safe to regenerate: existing output files are overwritten, while originals
are never modified.

## Install

1. Install Fabric Loader `0.15.11` (or newer) and Fabric API for Minecraft 1.20.1.
2. Put the jar for your loader in `.minecraft/mods`.
3. Start the game. The generated pack appears as **TChineseB-1.20.1.zip**.

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
