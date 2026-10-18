# Changelog

## Unreleased

- Renamed the project from GeyserNetease to NEGeyserExtension to avoid confusion with
  the pre-existing LoHJG/GeyserNetease project.
- The extension id is now `negeyser` (was `geyser-netease`). Move the existing
  `extensions/geyser-netease/` directory to `extensions/negeyser/` when upgrading.
- The jar is now `NEGeyserExtension.jar`; Java package is `io.github.negeyser`.

## 1.0.0

Initial public release.

- NetEase RakNet v8 support alongside regular Bedrock and Java clients.
- Local NetEase certificate-chain validation and Bedrock encryption handshake.
- NetEase codecs for protocols 819, 860 and 898.
- Block/entity metadata projection: hashed block ids, rebuilt item registry, filtered
  recipes, creative menu, biomes and entity identifiers.
- Inventory, container, item NBT and creative-menu support.
- Optional single-entry certificate chains for trusted room relays
  (`allow-single-chain`, `trusted-relay-addresses`).
- Early resource-pack handling for single-chain sessions.
- Per-client-type resource pack routing under `packs/netease` and
  `packs/international`.
- Per-client player-list skin confirmation de-duplication.
- Complete NetEase container-slot id translation.
- Custom-block aliases for obsidian/crying obsidian and mushroom blocks.
- Locator bar follows the Java `locator_bar` gamerule.
- Optional Chinese-name to pinyin transliteration.
