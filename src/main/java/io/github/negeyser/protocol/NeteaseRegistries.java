/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;

import java.lang.reflect.Method;

/**
 * Registers the NetEase Bedrock protocols as first-class versions in Geyser's versioned
 * registries. Once this has run, {@code forVersion(860)} returns NetEase item/block/tag data
 * instead of throwing, and Geyser's own translators emit NetEase-native content.
 */
public final class NeteaseRegistries {

    private static final int[] PROTOCOLS = {819, 860, 898};
    private static final Object LOCK = new Object();

    private static volatile boolean initialized;

    private NeteaseRegistries() {
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                // Reuse Geyser's newest tag table for now. Tag names are stable across these
                // versions; a NetEase-specific tag table can be layered on later if needed.
                // Both calls must go through reflection: Geyser-Spigot relocates fastutil, and
                // the generic value type of Registries.TAGS is a fastutil interface.
                Method forVersion = Registries.TAGS.getClass().getMethod("forVersion", int.class);
                Method register = findRegister(Registries.TAGS.getClass());
                Object tags = forVersion.invoke(Registries.TAGS, GameProtocol.DEFAULT_BEDROCK_PROTOCOL);

                for (int protocol : PROTOCOLS) {
                    int itemCount = NeteaseItemFilter.info(protocol).size();
                    if (itemCount == 0) {
                        throw new IllegalStateException("NetEase item registry for protocol "
                            + protocol + " could not be loaded");
                    }
                    BlockMappings blocks = NeteaseMappings.get(protocol);
                    BlockRegistries.BLOCKS.register(protocol, blocks);
                    Registries.ITEMS.register(protocol, NeteaseItemMappings.get(protocol));
                    register.invoke(Registries.TAGS, protocol, tags);
                    if (io.github.negeyser.NEGeyserExtension.LOG != null) {
                        io.github.negeyser.NEGeyserExtension.LOG.info(
                            "Registered NetEase registries for protocol " + protocol
                                + " (" + itemCount + " items)");
                    }
                }
                initialized = true;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to register NetEase protocol registries", e);
            }
        }
    }

    private static Method findRegister(Class<?> registryClass) throws NoSuchMethodException {
        for (Method method : registryClass.getMethods()) {
            if (method.getName().equals("register") && method.getParameterCount() == 2) {
                return method;
            }
        }
        throw new NoSuchMethodException("register(Object, Object) on " + registryClass);
    }

    public static void invalidate() {
        synchronized (LOCK) {
            initialized = false;
            NeteaseMappings.invalidate();
            NeteaseItemMappings.invalidate();
        }
    }
}
