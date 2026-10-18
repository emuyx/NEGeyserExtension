# NEGeyserExtension

[简体中文](README.md)

A Geyser extension that lets players on the **NetEase (China) edition of Minecraft
Bedrock** join the same server as international Bedrock and Java players.

- Built for upstream **Geyser 2.12.x** (extension API `2.12.0`); the Geyser jar is not
  replaced or modified.
- Supports NetEase client protocols **819 / 860 / 898** (Minecraft Bedrock 1.21.93 /
  1.21.124 / 1.21.130).
- Compatible with unmodified **Floodgate**: NetEase players receive a stable XUID/UUID
  and their client data is passed through like a regular Bedrock player.
- International Bedrock and Java players keep using Geyser's normal login, codec and
  registry paths.

> This is a community project. It is not affiliated with, endorsed by, or supported by
> GeyserMC, Mojang, Microsoft or NetEase.
>
> It is largely AI-assisted and **not actively maintained**; see
> [AI assistance](#ai-assistance).

## Installation

1. Install JDK 21 and Geyser 2.12.x, and enable the RakNet transport in Geyser
   (`transport: raknet` or `both`).
2. Put `NEGeyserExtension.jar` into Geyser's `extensions/` directory.
3. Start the server once. The extension creates `extensions/negeyser/config.yml`.
4. Adjust the configuration, then restart the server (or run `/geyser reload`).

```
extensions/
├── NEGeyserExtension.jar
└── negeyser/
    ├── config.yml
    └── packs/
        ├── netease/
        └── international/
```

## How it works

NetEase clients speak Bedrock, but differ from upstream in several places:

1. **RakNet v8.** The extension rebinds Geyser's RakNet listener. RakNet 8 connections
   use NetEase batch framing, start uncompressed and negotiate raw deflate; every other
   RakNet version keeps Geyser's stock pipeline (`UpstreamPacketHandler`).
2. **Different login chain.** NetEase sends a three-JWT chain signed by its own root
   certificate. The chain is validated locally (no Microsoft discovery) before the
   standard Bedrock encryption handshake. Room/cross-server relays may send only the
   final self-signed JWT; this is supported behind an explicit, off-by-default switch.
3. **Different packet layouts.** Codecs for 819/860/898 override `PlayerAuthInput`,
   text, command request, animate, resource pack stack, container open, inventory
   options, biome definitions and enchant options, and register NetEase-only packets
   (`ConfirmSkin`, `NetEaseJson`, `PyRpc`, `StoreBuySuccess`,
   `SetDimensionLocalTime`).
4. **Content projection.** NetEase sessions run on Geyser's newest protocol internally
   and project blocks/items/recipes/biomes back to the client's own registry:
   persistent block-state hashes, a rebuilt item registry, filtered recipes and
   creative menus, fallback items and blocks. International sessions are untouched.

The extension also handles client-specific quirks discovered during testing:

- NetEase container slot ids are shifted by one after `RECIPE_ITEMS`; requests and
  responses are translated in both directions.
- Duplicate player-list add/confirm pairs are suppressed, and skin changes are sent as
  remove → add → confirm, so NetEase clients do not hide the actor.
- Geyser's data-driven `geyser_custom:obsidian` / `crying_obsidian` blocks are aliased
  back to the client's vanilla blocks (they previously fell back to stone).
- The locator bar follows the Java `locator_bar` gamerule instead of staying on.
- Optional per-client-type resource pack routing.

## Requirements

- JDK 21
- Geyser 2.12.x with the RakNet transport enabled (`transport: raknet` or `both`)
- A NetEase Bedrock client on 1.21.93, 1.21.124 or 1.21.130
- Optional: Floodgate (no changes required)

NetEase PC clients and the NetherNet-only transport are not supported.

### Deploying through NetEase

If you plan to operate on the NetEase (China) platform — for example, to apply/入驻
NetEase and run a NetEase room or cross-server setup — the maintainers recommend
**布吉岛Geyser**, the NetEase Geyser branch this extension is developed and tested
with. Its source is available at <https://github.com/mc-dreamland/>.

## Configuration

```yaml
# Only accept NetEase clients. false (default): everyone can join.
only-netease-clients: false

# Message shown to international Bedrock clients when only-netease-clients is true.
disconnect-message: "This server only accepts NetEase clients."

# Verbose NetEase login/packet logging.
debug-mode: false

# Require the NetEase login profile environment to be "obt".
require-obt-environment: false

# Accept a single-entry, self-signed NetEase chain (room/cross-server relays).
# Keep this off unless players join through a trusted relay.
allow-single-chain: false

# Comma-separated source IPs allowed to send single-chain logins.
# Empty means any source is accepted (a warning is logged). Do not expose the
# Bedrock port directly to the internet in that case.
trusted-relay-addresses: ""

# Delay after all pack chunks before sending ResourcePackStack for single-chain
# sessions, in milliseconds.
single-chain-pack-stack-delay-ms: 1000

# Delay between resource pack chunks for NetEase clients, in milliseconds.
resource-pack-chunk-delay-ms: 200

# Reserved for a future NetEase PC implementation; has no effect.
allow-pc-clients: false

# Remove item definitions and creative entries that the client's registry lacks.
filter-unknown-items: true

# false: pass item stack NBT through unchanged.
# true: keep only display + extra-item-nbt.
strip-item-nbt: false

# Extra NBT/components kept when strip-item-nbt is true, comma separated.
extra-item-nbt: ""

# Convert CJK player names to pinyin and trim them to 16 ASCII characters.
translate-netease-names: false
```

### Single-chain room relays

NetEase room/cross-server relays may only forward the final self-signed JWT. With
`allow-single-chain: true`, the extension verifies that JWT against its own `x5u` key,
skips the NetEase root check, and requires `netease_uid` or `netease_uuid` in
`extraData`. `require-obt-environment` still applies. Restrict the relay with
`trusted-relay-addresses`; leaving it empty accepts any source and logs a warning.

Three-element chains keep their original validation, identity extraction and Floodgate
UUID behaviour.

### Resource pack routing

Geyser's own `packs/` directory stays shared by all clients. The extension adds two
per-client directories under its data folder:

- `packs/netease/` – sent only to NetEase clients;
- `packs/international/` – sent only to regular Bedrock clients.

Put `.zip` or `.mcpack` files there. Routed packs are registered with high priority so
they can override a shared pack that changes the same files. Restart (or
`/geyser reload`) after changing packs; existing sessions are not modified. Do not put
the same pack UUID in both Geyser's `packs/` and a routed directory.

## Floodgate

No replacement or patching is needed:

- keep Geyser's `auth-type` set to `floodgate`;
- the NetEase numeric uid is exposed as `AuthData.xuid`, and Floodgate derives the Java
  UUID from it;
- the client data JWT is still verified with the identity public key and parsed into
  `BedrockClientData`;
- international players continue through Geyser's normal Floodgate flow.

## Building

JDK 21 is required.

```bash
./gradlew clean build
```

Output: `build/libs/NEGeyserExtension.jar`.

Geyser, Cloudburst Protocol, Netty, fastutil, jose4j and Gson are provided by the
Geyser runtime and are not bundled. The only bundled dependency is
[pinyin4j](https://github.com/belerweb/pinyin4j) (BSD), used by the optional Chinese
name transliteration.

## Debugging

Set `debug-mode: true` in the extension config (and optionally in Geyser's config). The
extension logs `[NETEASE-TRACE]` pipeline stages, `[NETEASE-ITEM]`, `[NETEASE-RECIPE]`,
`[NETEASE-REQ]` / `[NETEASE-RESP]`, `[NETEASE-SKIN]`, `[NETEASE-PACKS]`,
`[NETEASE-GAMERULE]` and `[NETEASE-BLOCK]` diagnostics. Do not paste tokens,
certificates, server addresses or player names into public issues.

## Known limitations

- Only NetEase clients using RakNet (Windows/Android/iOS) are supported; the NetEase PC
  client uses a different protocol.
- Supported client versions: 1.21.93, 1.21.124 and 1.21.130. Other versions are
  rejected with a version-mismatch message.
- Newer Java content is projected down to 1.21.x. Unknown blocks become stone, unknown
  items become named paper, and new entities/biomes/recipes may be incomplete.
- Book and quill editing/signing does not persist correctly yet.
- Entity metadata is not filtered per client version; newly added entity types or
  metadata may misbehave.
- Bundle/dynamic-container stack requests are not fully verified (the NetEase wire
  container id for dynamic containers is outside the vanilla codec's map).
- Do not use NetherNet (`transport: nethernet`) only; use `raknet` or `both`.
- `/geyser reload` rebinds the listener. If anything looks wrong, a full restart is
  safer.
- The extension uses `sun.misc.Unsafe` to inject a session upstream wrapper and block
  mapping copies. Re-test after upgrading Geyser.
- The NetEase skin review HTTP API is not implemented. `ConfirmSkin` marks skins valid;
  add your own moderation if needed.

## AI assistance

Parts of this project were developed with the help of AI coding assistants
(including Claude Code). The maintainer reviews and tests AI-assisted changes where
possible, but AI-generated output can contain mistakes. Please verify behaviour in
your own environment and report issues.

Because this project is largely AI-assisted, the maintainer does **not** intend to
actively maintain it. Issues and pull requests may receive little or no response.
You are welcome to fork the project and continue development.

## License and credits

The extension source is MIT licensed; see [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Third-party/protocol data included under `src/main/resources/netease/` comes
from Nukkit-MOT, the community NetEase Geyser branch and NetEase client registries.
Those assets remain subject to their own terms and are **not** covered by this
project's MIT license.

The NetEase Geyser branch (布吉岛Geyser) and the NetEase Cloudburst Protocol fork used
during development come from <https://github.com/mc-dreamland/>. The reference
implementation [LoHJG/GeyserNetease](https://github.com/LoHJG/GeyserNetease) was also
useful.
