/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.protocol;

import java.util.HashMap;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

/**
 * Definition registry that resolves both hash-based network ids (what NetEase clients send)
 * and the legacy index-based ids (what Geyser's own code sometimes still uses).
 *
 * <p>The patched {@link BlockMappings} keeps blocks at their original array positions but gives
 * each block its persistent hash as runtime id. The default registry lookup indexes the array
 * with the runtime id, which cannot resolve hashes. Serverbound packets such as item-use
 * transactions carry hashes, so the session's codec helper is pointed at this registry.</p>
 */
public final class HashAwareBlockRegistry implements DefinitionRegistry<BlockDefinition> {

    private final GeyserBedrockBlock[] byIndex;
    private final Map<Integer, GeyserBedrockBlock> byHash;

    public HashAwareBlockRegistry(BlockMappings mappings) {
        this.byIndex = mappings.getBedrockRuntimeMap();
        this.byHash = new HashMap<>(byIndex.length);
        for (GeyserBedrockBlock block : byIndex) {
            if (block != null) {
                byHash.putIfAbsent(block.getRuntimeId(), block);
            }
        }
    }

    @Override
    public BlockDefinition getDefinition(int id) {
        GeyserBedrockBlock hashed = byHash.get(id);
        if (hashed != null) {
            return hashed;
        }
        if (id >= 0 && id < byIndex.length) {
            return byIndex[id];
        }
        return null;
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        if (definition == null) {
            return false;
        }
        // Accept both the patched definitions and equivalent definitions from the shared
        // registry; Geyser sometimes serializes static block definitions.
        return getDefinition(definition.getRuntimeId()) != null;
    }
}
