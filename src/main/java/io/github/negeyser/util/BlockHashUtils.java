/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.util;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.TreeMap;

/**
 * Computes the persistent Bedrock block-state network id (FNV-1a 32 of the block state NBT).
 * Bedrock 1.21.40+ resolves these hashes locally, which lets the bridge use Geyser's latest
 * block palette with NetEase clients without shipping a second set of mappings.
 */
public final class BlockHashUtils {

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;

    private BlockHashUtils() {
    }

    public static int fnv1a32Nbt(NbtMap tag) {
        if (tag == null) {
            return -2;
        }
        String name = tag.getString("name", "minecraft:unknown");
        if ("minecraft:unknown".equals(name)) {
            return -2;
        }

        NbtMap states = tag.getCompound("states");
        TreeMap<String, Object> sorted = new TreeMap<>();
        if (states != null) {
            sorted.putAll(states);
        }

        NbtMap stripped = NbtMap.builder()
            .putString("name", name)
            .putCompound("states", NbtMap.fromMap(sorted))
            .build();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             var writer = NbtUtils.createWriterLE(out)) {
            writer.writeTag(stripped);
            return fnv1a32(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to hash block state " + name, e);
        }
    }

    public static int fnv1a32(byte[] data) {
        int hash = FNV1_32_INIT;
        for (byte value : data) {
            hash ^= value & 0xFF;
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }
}
