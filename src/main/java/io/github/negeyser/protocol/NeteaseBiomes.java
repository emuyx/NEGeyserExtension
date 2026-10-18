/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

/**
 * Bedrock biome identifiers that exist in the NetEase client's biome registry.
 */
public final class NeteaseBiomes {

    private static final Set<String> KNOWN = load();

    private NeteaseBiomes() {
    }

    public static Set<String> knownBiomes() {
        return KNOWN;
    }

    private static Set<String> load() {
        try (InputStream stream = NeteaseBiomes.class.getResourceAsStream("/netease/stripped_biome_definitions.json")) {
            if (stream == null) {
                return Collections.emptySet();
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return Collections.unmodifiableSet(root.keySet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
