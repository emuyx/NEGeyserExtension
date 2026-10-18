/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.InputStream;

/**
 * The NetEase client's own Bedrock entity identifier registry.
 */
public final class NeteaseEntities {

    private static volatile NbtMap identifiers;

    private NeteaseEntities() {
    }

    public static NbtMap identifiers() {
        NbtMap result = identifiers;
        if (result == null) {
            synchronized (NeteaseEntities.class) {
                result = identifiers;
                if (result == null) {
                    try (InputStream stream = NeteaseEntities.class.getResourceAsStream("/netease/entity_identifiers.dat")) {
                        if (stream == null) {
                            result = NbtMap.EMPTY;
                        } else {
                            try (NBTInputStream reader = NbtUtils.createNetworkReader(stream, true, true)) {
                                result = (NbtMap) reader.readTag();
                            }
                        }
                    } catch (Exception e) {
                        result = NbtMap.EMPTY;
                    }
                    identifiers = result;
                }
            }
        }
        return result;
    }
}
