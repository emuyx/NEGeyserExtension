/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.util.BlockHashUtils;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * The block states known by the NetEase client, keyed by persistent FNV-1a hash.
 *
 * <p>Nukkit-MOT ships the client's own {@code runtime_block_states_netease_<protocol>.dat}
 * registries. Those are more accurate than converting the newer palette: in particular the
 * 1.21.124 client's crafting table/furnace states do not carry {@code custom_appearance}.
 * Older protocols fall back to the legacy block_palette resource.</p>
 */
public final class NeteaseBlocks {

    private record State(NbtMap nbt, int hash) {
    }

    public record KnownBlocks(Set<Integer> hashes, Map<String, List<State>> byName,
                              GeyserBedrockBlock fallback) {
        public boolean isEmpty() {
            return hashes.isEmpty();
        }

        public GeyserBedrockBlock map(int hash, NbtMap state) {
            if (hashes.isEmpty()) {
                return new GeyserBedrockBlock(hash, state);
            }
            if (hashes.contains(hash)) {
                return new GeyserBedrockBlock(hash, state);
            }

            String rawName = state.getString("name", "");
            String name = vanillaName(rawName);
            State mapped = find(name, state);
            if (mapped == null) {
                // Reuse Geyser's WildernessBoundConverter table (e.g. poplar -> pale oak,
                // cushion -> carpet, sulfur/cinnabar -> older blocks) before giving up.
                String fallbackName = NeteaseFallbacks.blockFallback(name);
                if (!fallbackName.equals(name)) {
                    mapped = find(fallbackName, state);
                    if (mapped != null && debug()) {
                        io.github.negeyser.NEGeyserExtension.LOG.info(
                            "[NETEASE-BLOCK] fallback " + rawName + " -> " + fallbackName);
                    }
                }
            }
            if (mapped != null) {
                if (!name.equals(rawName) && debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info(
                        "[NETEASE-BLOCK] custom block " + rawName + " -> " + name);
                }
                return new GeyserBedrockBlock(mapped.hash, mapped.nbt);
            }
            return fallback;
        }

        private State find(String name, NbtMap state) {
            List<State> candidates = byName.get(name);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            return choose(candidates, state);
        }

        private static boolean debug() {
            return io.github.negeyser.NEGeyserExtension.CONFIG != null
                && io.github.negeyser.NEGeyserExtension.CONFIG.debugMode();
        }

        /**
         * Geyser's built-in integrated pack replaces some Java blocks with data-driven custom
         * blocks so their mining speed matches Java; for example {@code geyser_custom:obsidian}.
         * Those definitions are not sent to NetEase clients (StartGame block properties are
         * cleared), so resolve the custom name back to the vanilla block the client knows.
         * Without this, obsidian/crying obsidian fall back to stone.
         */
        private static String vanillaName(String name) {
            String prefix = "geyser_custom:";
            return name.startsWith(prefix)
                ? "minecraft:" + name.substring(prefix.length())
                : name;
        }

        private static State choose(List<State> candidates, NbtMap geyserState) {
            NbtMap geyserStates = geyserState.getCompound("states");
            State best = null;
            int bestScore = -1;
            for (State candidate : candidates) {
                if (candidate.nbt.equals(geyserState)) {
                    return candidate;
                }
                int score = 0;
                NbtMap candidateStates = candidate.nbt.getCompound("states");
                if (geyserStates != null && candidateStates != null) {
                    for (Map.Entry<String, Object> entry : geyserStates.entrySet()) {
                        Object value = candidateStates.get(entry.getKey());
                        if (value != null && value.equals(entry.getValue())) {
                            score++;
                        }
                    }
                }
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
            return best;
        }
    }

    private static final KnownBlocks EMPTY = new KnownBlocks(Collections.emptySet(),
        Collections.emptyMap(), null);
    private static final Map<Integer, KnownBlocks> BY_PROTOCOL = new ConcurrentHashMap<>();

    private NeteaseBlocks() {
    }

    public static KnownBlocks forProtocol(int protocolVersion) {
        return BY_PROTOCOL.computeIfAbsent(protocolVersion, NeteaseBlocks::load);
    }

    private static KnownBlocks load(int protocolVersion) {
        try {
            List<NbtMap> entries = loadEntries(protocolVersion);
            if (entries == null) {
                return EMPTY;
            }
            Set<Integer> hashes = new HashSet<>();
            Map<String, List<State>> byName = new HashMap<>();
            GeyserBedrockBlock fallback = null;

            for (NbtMap entry : entries) {
                String name = entry.getString("name", "");
                if (name.isEmpty()) {
                    continue;
                }
                NbtMap states = entry.getCompound("states");
                NbtMap state = NbtMap.builder()
                    .putString("name", name)
                    .putCompound("states", states == null ? NbtMap.EMPTY : states)
                    .build();
                int hash = BlockHashUtils.fnv1a32Nbt(state);
                if (!hashes.add(hash)) {
                    continue;
                }
                byName.computeIfAbsent(name, ignored -> new ArrayList<>())
                    .add(new State(state, hash));

                if (fallback == null && "minecraft:stone".equals(name)
                    && (states == null || states.isEmpty())) {
                    fallback = new GeyserBedrockBlock(hash, state);
                }
            }

            if (fallback == null && !hashes.isEmpty()) {
                for (List<State> states : byName.values()) {
                    State first = states.get(0);
                    fallback = new GeyserBedrockBlock(first.hash, first.nbt);
                    break;
                }
            }

            Map<String, List<State>> immutable = new HashMap<>(byName.size());
            for (Map.Entry<String, List<State>> entry : byName.entrySet()) {
                immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new KnownBlocks(Collections.unmodifiableSet(hashes),
                Collections.unmodifiableMap(immutable), fallback);
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /**
     * NetEase client registries are a root NBT list of compounds; the legacy palette resource is
     * a compound with a {@code blocks} list.
     */
    private static List<NbtMap> loadEntries(int protocolVersion) throws Exception {
        String resource;
        if (protocolVersion == 860) {
            resource = "/netease/block_states_netease_860.dat";
        } else if (protocolVersion == 819) {
            resource = "/netease/block_states_netease_819.dat";
        } else {
            resource = protocolVersion >= 860
                ? "/netease/block_palette.1_21_120.nbt"
                : "/netease/block_palette.1_21_93.nbt";
        }

        try (InputStream raw = NeteaseBlocks.class.getResourceAsStream(resource)) {
            if (raw == null) {
                return null;
            }
            try (NBTInputStream reader = new NBTInputStream(
                new DataInputStream(new GZIPInputStream(raw)), true, true)) {
                Object tag = reader.readTag();
                if (tag instanceof NbtList<?> list) {
                    List<NbtMap> result = new ArrayList<>(list.size());
                    for (Object value : list) {
                        if (value instanceof NbtMap map) {
                            result.add(map);
                        }
                    }
                    return result;
                }
                if (tag instanceof NbtMap root) {
                    return root.getList("blocks", NbtType.COMPOUND);
                }
                return null;
            }
        }
    }
}
