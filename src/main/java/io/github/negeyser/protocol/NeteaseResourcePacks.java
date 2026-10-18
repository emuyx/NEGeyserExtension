/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.NEGeyserExtension;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.PriorityOption;
import org.geysermc.geyser.session.GeyserSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Per-client resource pack routing.
 *
 * <p>Packs registered in Geyser's own {@code packs} folder stay shared: they are loaded into
 * {@code Registries.RESOURCE_PACKS} and are therefore present in every session's event before
 * this class runs. This class adds two extra, per-session directories under the extension data
 * folder:</p>
 *
 * <ul>
 *     <li>{@code packs/netease} – sent only to NetEase (RakNet v8) sessions;</li>
 *     <li>{@code packs/international} – sent only to regular Bedrock sessions.</li>
 * </ul>
 *
 * <p>Both sets are registered with {@link PriorityOption#HIGH} so platform packs override a
 * shared pack from Geyser's folder when they modify the same files.</p>
 */
public final class NeteaseResourcePacks {

    private static final String NETEASE_DIRECTORY = "netease";
    private static final String INTERNATIONAL_DIRECTORY = "international";

    private static volatile List<ResourcePack> neteasePacks = List.of();
    private static volatile List<ResourcePack> internationalPacks = List.of();
    private static final Set<UUID> reportedDuplicates = ConcurrentHashMap.newKeySet();

    private NeteaseResourcePacks() {
    }

    /**
     * Creates the routing folders if needed and loads every {@code .zip}/{@code .mcpack} in
     * them. Called on startup and on {@code /geyser reload}.
     */
    public static synchronized void reload(Path dataFolder) {
        Path packRoot = dataFolder.resolve("packs");
        Path neteaseDirectory = packRoot.resolve(NETEASE_DIRECTORY);
        Path internationalDirectory = packRoot.resolve(INTERNATIONAL_DIRECTORY);

        try {
            Files.createDirectories(neteaseDirectory);
            Files.createDirectories(internationalDirectory);
        } catch (IOException e) {
            warn("Could not create the resource pack directories: " + e.getMessage());
        }

        neteasePacks = load(neteaseDirectory, "NetEase");
        internationalPacks = load(internationalDirectory, "international");
        info("[NETEASE-PACKS] loaded " + neteasePacks.size() + " NetEase-only and "
            + internationalPacks.size() + " international-only pack(s); Geyser packs/ stays shared");
    }

    /**
     * Registers the routed packs for the session currently loading resource packs.
     */
    public static void apply(SessionLoadResourcePacksEvent event) {
        boolean netease = event.connection() instanceof GeyserSession session
            && session.getUpstream() instanceof SpoofedUpstream;
        List<ResourcePack> packs = netease ? neteasePacks : internationalPacks;

        for (ResourcePack pack : packs) {
            try {
                event.register(pack, PriorityOption.HIGH);
            } catch (Throwable e) {
                UUID uuid = pack.uuid();
                if (reportedDuplicates.add(uuid)) {
                    warn("[NETEASE-PACKS] could not register pack " + uuid
                        + " (already registered or invalid): " + e.getMessage());
                }
            }
        }

        if (debug() && !packs.isEmpty()) {
            info("[NETEASE-PACKS] registered " + packs.size() + " pack(s) for "
                + (netease ? "NetEase" : "international") + " session "
                + event.connection().bedrockUsername());
        }
    }

    private static List<ResourcePack> load(Path directory, String label) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<ResourcePack> packs = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".zip") && !name.endsWith(".mcpack")) {
                    continue;
                }
                try {
                    packs.add(ResourcePack.create(PackCodec.path(file)));
                } catch (Throwable e) {
                    warn("[NETEASE-PACKS] could not load " + label + " pack "
                        + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            warn("[NETEASE-PACKS] could not read " + directory + ": " + e.getMessage());
        }
        return List.copyOf(packs);
    }

    private static boolean debug() {
        return NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.debugMode();
    }

    private static void info(String message) {
        if (NEGeyserExtension.LOG != null) {
            NEGeyserExtension.LOG.info(message);
        }
    }

    private static void warn(String message) {
        if (NEGeyserExtension.LOG != null) {
            NEGeyserExtension.LOG.warning(message);
        }
    }
}
