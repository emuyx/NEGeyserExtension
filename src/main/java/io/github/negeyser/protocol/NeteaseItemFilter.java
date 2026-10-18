/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Item identifiers that exist in the NetEase client's own item registry.
 *
 * <p>Geyser 2.12 runs on a much newer Bedrock item registry. Sending brand new item identifiers
 * (and their component NBT) to a 1.21.93/1.21.124/1.21.130 client can make the NetEase client
 * fail while loading item components or the creative menu. The bundled lists come from the
 * NetEase Geyser branch and are used to strip definitions the client cannot understand.</p>
 */
public final class NeteaseItemFilter {

    public record ItemInfo(int runtimeId, int version, boolean componentBased, NbtMap components) {
    }

    private static final Map<Integer, Map<String, ItemInfo>> BY_PROTOCOL = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> COMPONENT_KEYS = new ConcurrentHashMap<>();

    private NeteaseItemFilter() {
    }

    public static Set<String> knownItems(int protocolVersion) {
        return info(protocolVersion).keySet();
    }

    public static Map<String, ItemInfo> info(int protocolVersion) {
        return BY_PROTOCOL.computeIfAbsent(protocolVersion, NeteaseItemFilter::load);
    }

    /**
     * Rebuilds an item definition using the NetEase client's own version, component flag and
     * component NBT while keeping the runtime id Geyser assigned to the item. Returns null when
     * the item does not exist in the client version and should be removed.
     */
    /**
     * Removes top-level item components that do not exist in the NetEase client version.
     * Item stack NBT uses component names as keys; unknown/new components can crash the client.
     */
    public static NbtMap filterTag(int protocolVersion, NbtMap tag) {
        if (tag == null || tag.isEmpty()) {
            return tag;
        }
        Set<String> allowed = componentKeys(protocolVersion);
        if (allowed.isEmpty()) {
            return tag;
        }
        NbtMapBuilder builder = NbtMap.builder();
        for (Map.Entry<String, Object> entry : tag.entrySet()) {
            if ("display".equals(entry.getKey()) || allowed.contains(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    public static Set<String> componentKeys(int protocolVersion) {
        return COMPONENT_KEYS.computeIfAbsent(protocolVersion, ignored -> {
            try (InputStream stream = NeteaseItemFilter.class.getResourceAsStream(componentsResource(protocolVersion))) {
                if (stream == null) {
                    return Collections.emptySet();
                }
                NbtMap root = (NbtMap) NbtUtils.createGZIPReader(stream, true, true).readTag();
                Set<String> keys = new HashSet<>();
                for (Map.Entry<String, Object> entry : root.entrySet()) {
                    if (entry.getValue() instanceof NbtMap components) {
                        keys.addAll(components.keySet());
                    }
                }
                return Collections.unmodifiableSet(keys);
            } catch (Exception e) {
                return Collections.emptySet();
            }
        });
    }

    private static String componentsResource(int protocolVersion) {
        if (protocolVersion == 860) {
            // Exact NetEase 1.21.124 client registry shipped by Nukkit-MOT.
            return "/netease/item_components_netease_860.nbt";
        } else if (protocolVersion >= 898) {
            return "/netease/item_components.1_21_130.nbt";
        }
        return "/netease/item_components.1_21_93.nbt";
    }

    public static ItemDefinition rebuild(ItemDefinition original, int protocolVersion) {
        ItemInfo info = info(protocolVersion).get(original.getIdentifier());
        if (info == null) {
            return null;
        }
        return new SimpleItemDefinition(original.getIdentifier(), original.getRuntimeId(),
            ItemVersion.from(info.version()), info.componentBased(), info.components());
    }

    /**
     * Builds the definition with the runtime id from the NetEase client's own runtime item
     * states. Used when constructing a native NetEase {@code ItemMappings} registry.
     */
    public static ItemDefinition definition(String identifier, int protocolVersion) {
        ItemInfo info = info(protocolVersion).get(identifier);
        if (info == null || info.runtimeId() == Integer.MIN_VALUE) {
            return null;
        }
        return new SimpleItemDefinition(identifier, info.runtimeId(),
            ItemVersion.from(info.version()), info.componentBased(), info.components());
    }

    public static int maxRuntimeId(int protocolVersion) {
        int max = 0;
        for (ItemInfo info : info(protocolVersion).values()) {
            max = Math.max(max, info.runtimeId());
        }
        return max;
    }

    private static Map<String, ItemInfo> load(int protocolVersion) {
        String json;
        if (protocolVersion == 860) {
            json = "/netease/runtime_item_states_netease_860.json";
        } else if (protocolVersion >= 898) {
            json = "/netease/runtime_item_states.1_21_130.json";
        } else {
            json = "/netease/runtime_item_states.1_21_93.json";
        }
        String components = componentsResource(protocolVersion);

        try (InputStream componentStream = NeteaseItemFilter.class.getResourceAsStream(components)) {
            NbtMap componentRoot = componentStream == null ? NbtMap.EMPTY
                : (NbtMap) NbtUtils.createGZIPReader(componentStream, true, true).readTag();

            try (InputStream stream = NeteaseItemFilter.class.getResourceAsStream(json)) {
                if (stream == null) {
                    return Collections.emptyMap();
                }
                JsonArray array = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
                Map<String, ItemInfo> result = new HashMap<>(array.size());
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    JsonElement nameElement = object.get("name");
                    if (nameElement == null || !nameElement.isJsonPrimitive()) {
                        continue;
                    }
                    String name = nameElement.getAsString();
                    int runtimeId = object.has("id") ? object.get("id").getAsInt() : Integer.MIN_VALUE;
                    int version = object.has("version") ? object.get("version").getAsInt() : 0;
                    boolean componentBased = object.has("componentBased") && object.get("componentBased").getAsBoolean();
                    NbtMap data = componentRoot.getCompound(name);
                    result.put(name, new ItemInfo(runtimeId, version, componentBased,
                        data == null ? NbtMap.EMPTY : data));
                }
                return Collections.unmodifiableMap(result);
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
