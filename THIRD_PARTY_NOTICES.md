# Third-party notices

NEGeyserExtension is licensed under the MIT License (see `LICENSE`). It builds on and
redistributes data from the following projects. The MIT grant in this repository does
not override the terms of these third parties.

## Geyser / GeyserMC

- Source: <https://github.com/GeyserMC/Geyser>
- License: MIT
- Usage: parts of `handler/LoggingPacketHandler.java` and `handler/UpstreamHandlerBase.java`
  are adapted from Geyser. The extension API, protocol structures and packet semantics
  are also based on Geyser.
- Copyright belongs to the GeyserMC contributors.

## pinyin4j

- Coordinates: `com.belerweb:pinyin4j:2.5.1`
- Source: <https://github.com/belerweb/pinyin4j>
- License: BSD
- Usage: Chinese-to-pinyin transliteration for the optional `translate-netease-names`
  feature. The library classes are bundled inside the released extension jar.

## Nukkit-MOT

- Source: <https://github.com/MemoriesOfTime/Nukkit-MOT>
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- Usage: the NetEase client block and item registry data shipped under
  `src/main/resources/netease/` (`block_states_netease_819.dat`,
  `block_states_netease_860.dat`, `runtime_item_states_netease_860.json`,
  `item_components_netease_860.nbt`) originates from Nukkit-MOT.

## 布吉岛Geyser (NetEase Geyser branch) and Protocol-netease

- Source: <https://github.com/mc-dreamland/>
- Usage: the extension is developed and tested against this NetEase Geyser branch,
  and its protocol knowledge / resource data comes from it. The NetEase variant of the
  Cloudburst Protocol library (`Protocol-3.0-netease`) also originates from this
  project.

## NetEase client data

The remaining files under `src/main/resources/netease/` (legacy block palettes,
creative item lists, item component/state tables, biome definitions and entity
identifiers) were extracted from or generated using 布吉岛Geyser and NetEase Bedrock
client registries.

Minecraft is a trademark of Mojang Synergies AB. NetEase is a trademark of NetEase,
Inc. This project is not affiliated with Mojang, Microsoft or NetEase. Those
third-party client assets are not authored by this project and are not covered by
this repository's MIT license; they are included solely for protocol compatibility.
If you redistribute this project or its jar, you are responsible for complying with
the applicable third-party terms.

## Reference-only sources

The following are used during development but are **not** distributed with this
repository or its releases:

- Geyser source and standalone builds (`Geyser-master.zip`, `Geyser-Standalone.jar`)
- Cloudburst Protocol sources (`Protocol-3.0*.zip`)
- Nukkit-MOT source tree
- the NetEase Geyser branch source tree
