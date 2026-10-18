/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.config;

import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the flat {@code config.yml} used by this extension without pulling in a YAML library.
 * Only simple {@code key: value} entries and comments are supported, which is all this extension
 * needs.
 */
public final class NeteaseConfigLoader {

    private static final String DEFAULT_CONFIG = """
            # Whether only NetEase clients may connect.
            # false (default): regular Bedrock Edition, NetEase, and Java players can all join.
            only-netease-clients: false

            # Message shown to international Bedrock clients when only-netease-clients is true.
            disconnect-message: "This server only accepts NetEase clients."

            # Verbose logging for NetEase login and packet handling.
            debug-mode: false

            # Require the NetEase login profile environment to be "obt".
            # Leave false unless your server only accepts production NetEase clients.
            require-obt-environment: false

            # Accept a single-entry, self-signed NetEase certificate chain. Some NetEase room
            # (cross-server) relays terminate the login and forward only the last JWT. Only
            # enable this when players join through such a trusted relay.
            allow-single-chain: false

            # Comma-separated source IPs allowed to send single-chain logins, e.g. "10.0.0.5".
            # When empty, every source is accepted (a warning is logged). Never expose the
            # Bedrock port directly to the public internet while this is empty.
            trusted-relay-addresses: ""

            # How long to wait after all resource pack chunks are sent before sending the
            # resource pack stack for single-chain room sessions, in milliseconds. Those
            # relays acknowledge packs early, so the client needs the download to finish first.
            single-chain-pack-stack-delay-ms: 1000

            # Delay between resource pack chunks sent to NetEase clients, in milliseconds.
            # NetEase clients are slower at downloading packs. 0 disables the delay.
            resource-pack-chunk-delay-ms: 200

            # Allow NetEase PC clients. NetEase PC uses a separate protocol revision and is
            # not supported by this extension; this option exists for future use.
            allow-pc-clients: false

            # Remove item definitions and creative-menu entries that do not exist in the
            # connecting client's own item registry. Prevents the client from crashing on
            # items/components introduced after its version.
            filter-unknown-items: true

            # false: pass item stack NBT through unchanged (books, dyed items, containers,
            # trims, durability and enchantments work). true: keep only display + extra-item-nbt.
            strip-item-nbt: false

            # Extra item stack NBT/components to keep when strip-item-nbt is true.
            # Comma separated, e.g. "Damage,ench".
            extra-item-nbt: ""

            # Convert CJK characters in NetEase player names to pinyin and trim to 16
            # characters (ASCII letters/digits/underscore). Disabled by default; leave it off
            # if the server/plugins support Chinese names.
            translate-netease-names: false
            """;

    private NeteaseConfigLoader() {
    }

    public static NeteaseConfig load(Extension extension) {
        Path file = extension.dataFolder().resolve("config.yml");
        NeteaseConfig config = new NeteaseConfig();
        ExtensionLogger logger = extension.logger();

        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }

            try (InputStream stream = Files.newInputStream(file)) {
                parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), config);
            }
        } catch (IOException e) {
            logger.warning("Could not read config.yml, using defaults: " + e.getMessage());
        }

        return config;
    }

    private static void parse(String yaml, NeteaseConfig config) {
        for (String rawLine : yaml.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }

            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }

            // Ignore inline comments for unquoted scalars.
            int comment = value.indexOf(" #");
            if (comment >= 0) {
                value = value.substring(0, comment).trim();
            }

            switch (key) {
                case "only-netease-clients" -> config.setOnlyNeteaseClients(parseBoolean(value));
                case "disconnect-message" -> {
                    if (!value.isBlank()) {
                        config.setDisconnectMessage(value);
                    }
                }
                case "debug-mode" -> config.setDebugMode(parseBoolean(value));
                case "require-obt-environment" -> config.setRequireObtEnvironment(parseBoolean(value));
                case "resource-pack-chunk-delay-ms" -> {
                    try {
                        config.setResourcePackChunkDelayMs(Math.max(0, Integer.parseInt(value)));
                    } catch (NumberFormatException ignored) {
                        // Keep default.
                    }
                }
                case "allow-pc-clients" -> config.setAllowPcClients(parseBoolean(value));
                case "allow-single-chain" -> config.setAllowSingleChain(parseBoolean(value));
                case "trusted-relay-addresses" -> {
                    java.util.Set<String> addresses = new java.util.HashSet<>();
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            addresses.add(part.trim());
                        }
                    }
                    config.setTrustedRelayAddresses(addresses);
                }
                case "single-chain-pack-stack-delay-ms" -> {
                    try {
                        config.setSingleChainPackStackDelayMs(Math.max(0, Integer.parseInt(value)));
                    } catch (NumberFormatException ignored) {
                        // Keep default.
                    }
                }
                case "filter-unknown-items" -> config.setFilterUnknownItems(parseBoolean(value));
                case "strip-item-nbt" -> config.setStripItemNbt(parseBoolean(value));
                case "extra-item-nbt" -> {
                    java.util.Set<String> keys = new java.util.HashSet<>();
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            keys.add(part.trim());
                        }
                    }
                    config.setExtraItemNbt(keys);
                }
                case "translate-netease-names" -> config.setTranslateNeteaseNames(parseBoolean(value));
                default -> {
                    // Unknown option: ignore so older configs keep working.
                }
            }
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
