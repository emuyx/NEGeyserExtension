/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.util.BlockHashUtils;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Creates a per-session copy of {@link BlockMappings} whose runtime ids are the persistent
 * FNV-1a block state hashes understood by modern Bedrock clients.
 *
 * <p>The shared registry is never modified: regular Bedrock sessions keep using sequential
 * runtime ids.</p>
 */
public final class BlockMappingsPatcher {

    private static final Unsafe UNSAFE;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private BlockMappingsPatcher() {
    }

    public static BlockMappings cloneAndHash(BlockMappings original, int protocolVersion) {
        NeteaseBlocks.KnownBlocks known = NeteaseBlocks.forProtocol(protocolVersion);
        try {
            BlockMappings copy = (BlockMappings) UNSAFE.allocateInstance(BlockMappings.class);
            for (Field field : BlockMappings.class.getDeclaredFields()) {
                long offset = UNSAFE.objectFieldOffset(field);
                Object value = UNSAFE.getObject(original, offset);
                UNSAFE.putObject(copy, offset, cloneValue(value, known));
            }
            return copy;
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to create a NetEase block mapping copy", e);
        }
    }

    private static Object cloneValue(Object value, NeteaseBlocks.KnownBlocks known) {
        if (value instanceof GeyserBedrockBlock[] array) {
            GeyserBedrockBlock[] replacement = new GeyserBedrockBlock[array.length];
            for (int i = 0; i < array.length; i++) {
                replacement[i] = wrap(array[i], known);
            }
            return replacement;
        }
        if (value instanceof GeyserBedrockBlock block) {
            return wrap(block, known);
        }
        // Maps and other collections are intentionally shared: they are read-only after startup
        // and touching them would require platform-specific fastutil classes (Velocity relocates
        // fastutil inside its Geyser jar).
        return value;
    }

    private static GeyserBedrockBlock wrap(GeyserBedrockBlock block, NeteaseBlocks.KnownBlocks known) {
        if (block == null || block.getState() == null) {
            return block;
        }
        NbtMap state = block.getState();
        int hash = BlockHashUtils.fnv1a32Nbt(state);
        GeyserBedrockBlock mapped = known.map(hash, state);
        return mapped != null ? mapped : block;
    }
}
