# Changelog

## Unreleased

- Renamed the project from GeyserNetease to NEGeyserExtension to avoid confusion with
  the pre-existing LoHJG/GeyserNetease project.
- The extension id is now `negeyser` (was `geyser-netease`). Move the existing
  `extensions/geyser-netease/` directory to `extensions/negeyser/` when upgrading.
- The jar is now `NEGeyserExtension.jar`; Java package is `io.github.negeyser`.
- Single-chain early-3 resource pack flow no longer waits for cached packs. It waits only
  for packs that receive chunk requests, has fallback/max-wait timers, and can resend
  duplicate chunks. New options: `single-chain-pack-fallback-ms`,
  `single-chain-pack-max-wait-ms`, `single-chain-resend-chunks`.
- Stack scheduling is now debounced: each new chunk request cancels and restarts the
  timer, so the stack always waits the full delay after the **last** requested pack
  completes. The default `single-chain-pack-stack-delay-ms` is now 2000.
- Added first-pass 26.3 entity fallbacks for NetEase targets: cushion → falling block
  (carpet), sulfur cube → slime, poplar boats → clamped boat variants, and for older
  targets nautilus → squid, zombie nautilus → glow squid, camel husk → camel,
  parched → skeleton. Entity metadata that is invalid for the fallback is stripped.
- Unnamed fallback entities now show their real names on an always-visible nameplate;
  player-applied name tags are preserved.
- NetEase block resolution now reuses Geyser's WildernessBoundConverter fallbacks
  (poplar → pale oak, cushion → carpet, sulfur/cinnabar → older blocks) before
  falling back to stone.
- Expanded item fallbacks: spears → trident/sword, nautilus armor → horse armor,
  netherite horse armor → leather, 1.21.11/26.x spawn eggs, sulfur cube bucket,
  music disc, sulfur spike, golden dandelion, and unknown explorer maps.
- Added defensive packet sanitisation for NetEase sessions: entity metadata types
  unknown to the target codec are removed, entity properties are cleared (their
  SyncEntityProperty definitions are not sent), and unsupported sound/level/entity
  events are dropped instead of throwing during encoding.
- Installed a NetEase-specific container-slot TypeMap in the codec helper. Bundle
  dynamic containers (NetEase wire id 64) now decode/encode correctly instead of
  throwing during item stack request/response handling.
- NetEase clients insert one extra particle after `SNEEZE`, so particle ids above it
  are now shifted by one (parity with Nukkit-MOT's `Particle#getMultiversionId`).
  This covers particle `LevelEventPacket`s and the `AREA_EFFECT_CLOUD_PARTICLE`
  entity metadata value; the shifted maps are installed per NetEase codec, so
  particle ids are encoded correctly without touching vanilla sessions.

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
