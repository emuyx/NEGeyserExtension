/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.BlockMappings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily builds the hash-based block mappings shared by all NetEase sessions.
 *
 * <p>All NetEase sessions use Geyser's newest block palette internally, so the patched copy is
 * immutable and can be reused. Building it is relatively expensive (every block state is
 * serialized once to compute its hash).</p>
 */
public final class NeteaseMappings {

    private static final Map<Integer, BlockMappings> MAPPINGS = new ConcurrentHashMap<>();

    private NeteaseMappings() {
    }

    public static BlockMappings get() {
        return get(898);
    }

    public static BlockMappings get(int protocolVersion) {
        return MAPPINGS.computeIfAbsent(protocolVersion, version -> BlockMappingsPatcher.cloneAndHash(
            BlockRegistries.BLOCKS.forVersion(GameProtocol.DEFAULT_BEDROCK_PROTOCOL), version));
    }

    public static void invalidate() {
        MAPPINGS.clear();
    }
}
