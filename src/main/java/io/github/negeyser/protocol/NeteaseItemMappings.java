/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemCategory;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import org.geysermc.geyser.inventory.item.StoredItemMappings;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.ItemMappings;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a NetEase-flavored {@link ItemMappings} for one Bedrock protocol version.
 *
 * <p>Upstream Geyser only populates item mappings for its newest palette versions. For a NetEase
 * session we need the same structure, but with the runtime ids and item components from the
 * NetEase client's own registry. Java items that do not exist in that registry keep a private
 * fallback definition id (never sent to the client); {@code SpoofedUpstream} replaces them with
 * paper + display name on the way out, so they cannot collide with NetEase ids.</p>
 */
public final class NeteaseItemMappings {

    private static final Map<Integer, ItemMappings> MAPPINGS = new ConcurrentHashMap<>();

    private NeteaseItemMappings() {
    }

    public static ItemMappings get(int protocolVersion) {
        return MAPPINGS.computeIfAbsent(protocolVersion, NeteaseItemMappings::build);
    }

    public static void invalidate() {
        MAPPINGS.clear();
    }

    private static ItemMappings build(int protocolVersion) {
        ItemMappings newest = Registries.ITEMS.forVersion(GameProtocol.DEFAULT_BEDROCK_PROTOCOL);
        BlockMappings blocks = NeteaseMappings.get(protocolVersion);
        Map<String, NeteaseItemFilter.ItemInfo> info = NeteaseItemFilter.info(protocolVersion);

        int fallbackId = NeteaseItemFilter.maxRuntimeId(protocolVersion) + 1;
        ItemMapping[] source = newest.getItems();
        ItemMapping[] items = new ItemMapping[source.length];
        Map<String, ItemMapping> byIdentifier = new HashMap<>(source.length);
        Map<Item, ItemMapping> byJavaItem = new HashMap<>(source.length);
        Map<Integer, ItemDefinition> newDefinitionByOldId = new HashMap<>(source.length);
        // Prefer the block state the NetEase client's creative menu uses for this identifier.
        // ItemMappings#getMapping compares block definitions by identity for block items.
        Map<String, BlockDefinition> creativeBlocks = loadCreativeBlocks(protocolVersion, blocks);

        for (int i = 0; i < source.length; i++) {
            ItemMapping original = source[i];
            if (original == null) {
                items[i] = null;
                continue;
            }
            String identifier = original.getBedrockIdentifier();
            NeteaseItemFilter.ItemInfo itemInfo = info.get(identifier);
            boolean known = itemInfo != null && itemInfo.runtimeId() != Integer.MIN_VALUE;

            ItemDefinition definition;
            if (known) {
                definition = new SimpleItemDefinition(identifier, itemInfo.runtimeId(),
                    ItemVersion.from(itemInfo.version()), itemInfo.componentBased(), itemInfo.components());
            } else {
                // Not in the NetEase registry. Give it an id outside the NetEase range so that
                // inbound NetEase ids can never resolve to this mapping by accident.
                definition = new SimpleItemDefinition(identifier, fallbackId++,
                    ItemVersion.NONE, false, null);
            }

            BlockDefinition block = creativeBlocks.get(identifier);
            if (block == null) {
                block = remapBlock(original.getBedrockBlockDefinition(), blocks);
            }
            ItemMapping mapping = ItemMapping.builder()
                .bedrockIdentifier(identifier)
                .bedrockDefinition(definition)
                .bedrockData(original.getBedrockData())
                .bedrockBlockDefinition(block)
                .translationString(original.getTranslationString())
                .customItemDefinitions(original.getCustomItemDefinitions())
                .containsV1Mappings(original.isContainsV1Mappings())
                .javaItem(original.getJavaItem())
                .build();

            items[i] = mapping;
            byJavaItem.put(original.getJavaItem(), mapping);
            newDefinitionByOldId.put(original.getBedrockDefinition().getRuntimeId(), definition);
            if (known) {
                byIdentifier.put(identifier, mapping);
            }
        }

        Map<Integer, ItemDefinition> itemDefinitions = newDefinitionMap(newest);
        for (Map.Entry<String, NeteaseItemFilter.ItemInfo> entry : info.entrySet()) {
            NeteaseItemFilter.ItemInfo itemInfo = entry.getValue();
            if (itemInfo.runtimeId() == Integer.MIN_VALUE) {
                continue;
            }
            itemDefinitions.put(itemInfo.runtimeId(), new SimpleItemDefinition(entry.getKey(),
                itemInfo.runtimeId(), ItemVersion.from(itemInfo.version()), itemInfo.componentBased(),
                itemInfo.components()));
        }

        Map<Integer, ItemMapping> lightBlocks = newLightBlockMap(newest);
        Map<String, ItemMapping> specialByIdentifier = new HashMap<>();
        for (int level = 0; level <= 15; level++) {
            String identifier = "minecraft:light_block_" + level;
            NeteaseItemFilter.ItemInfo itemInfo = info.get(identifier);
            if (itemInfo == null || itemInfo.runtimeId() == Integer.MIN_VALUE) {
                continue;
            }
            ItemDefinition definition = new SimpleItemDefinition(identifier, itemInfo.runtimeId(),
                ItemVersion.from(itemInfo.version()), itemInfo.componentBased(), itemInfo.components());
            ItemMapping mapping = ItemMapping.builder()
                .javaItem(Items.LIGHT)
                .bedrockIdentifier(identifier)
                .bedrockDefinition(definition)
                .bedrockData(0)
                .bedrockBlockDefinition(null)
                .customItemDefinitions(null)
                .build();
            lightBlocks.put(itemInfo.runtimeId(), mapping);
            specialByIdentifier.put(identifier, mapping);
        }

        ItemMapping lodestone = buildLodestone(info, specialByIdentifier);
        if (lodestone == null) {
            lodestone = newest.getLodestoneCompass();
        }

        CreativeData creative = buildCreative(protocolVersion, info, blocks, byIdentifier, specialByIdentifier);
        if (creative == null) {
            // Fallback: filter the newest creative list. Layout will not exactly match the
            // NetEase client, but remains usable if the bundled resource is missing.
            List<CreativeItemData> creativeItems = new ArrayList<>(newest.getCreativeItems().size());
            for (CreativeItemData entry : newest.getCreativeItems()) {
                ItemData item = entry == null ? null : entry.getItem();
                if (item == null || item.getDefinition() == null) {
                    continue;
                }
                String identifier = item.getDefinition().getIdentifier();
                ItemMapping mapping = byIdentifier.getOrDefault(identifier, specialByIdentifier.get(identifier));
                if (mapping == null) {
                    continue;
                }
                ItemData translated = item.toBuilder()
                    .definition(mapping.getBedrockDefinition())
                    .blockDefinition(mapping.getBedrockBlockDefinition())
                    .build();
                creativeItems.add(new CreativeItemData(translated, entry.getNetId(), entry.getGroupId()));
            }
            creative = new CreativeData(newest.getCreativeItemGroups(), creativeItems);
        }

        // Reuse the bucket definition list from the newest mappings and translate each entry by
        // runtime id. Avoid Item#javaKey(): Geyser-Spigot relocates Adventure's Key class.
        List<ItemDefinition> buckets = new ArrayList<>();
        for (ItemDefinition oldDefinition : newest.getBuckets()) {
            ItemDefinition translated = newDefinitionByOldId.get(oldDefinition.getRuntimeId());
            if (translated != null) {
                buckets.add(translated);
            }
        }

        Integer[] zeroBlockDefinitionRuntimeId = buildZeroBlockIds(items);

        ItemMappings built = copy(newest, items, itemDefinitions, lightBlocks, lodestone,
            creative.groups(), creative.items(), new StoredItemMappings(byJavaItem), buckets,
            zeroBlockDefinitionRuntimeId);
        validateCreativeMappings(built, creative, protocolVersion);
        return built;
    }

    private static void validateCreativeMappings(ItemMappings mappings, CreativeData creative,
                                                 int protocolVersion) {
        if (!debug()) {
            return;
        }
        int failed = 0;
        List<String> samples = new ArrayList<>();
        for (CreativeItemData entry : creative.items()) {
            ItemMapping mapping = mappings.getMapping(entry.getItem());
            if (mapping == ItemMapping.AIR) {
                failed++;
                if (samples.size() < 12) {
                    samples.add(entry.getItem().getDefinition().getIdentifier());
                }
            }
        }
        io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-CREATIVE] protocol "
            + protocolVersion + " reverse mapping failures=" + failed + "/" + creative.items().size()
            + (samples.isEmpty() ? "" : " samples=" + samples));
    }

    private static boolean debug() {
        return io.github.negeyser.NEGeyserExtension.CONFIG != null
            && io.github.negeyser.NEGeyserExtension.CONFIG.debugMode();
    }

    private static String creativeResource(int protocolVersion) {
        return protocolVersion >= 898 ? "/netease/creative_items.1_21_130.json"
            : protocolVersion >= 860 ? "/netease/creative_items.1_21_120.json"
            : "/netease/creative_items.1_21_93.json";
    }

    private static Map<String, BlockDefinition> loadCreativeBlocks(int protocolVersion,
                                                                   BlockMappings blocks) {
        Map<String, BlockDefinition> result = new HashMap<>();
        try (InputStream stream = NeteaseItemMappings.class.getResourceAsStream(
            creativeResource(protocolVersion))) {
            if (stream == null) {
                return result;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) {
                return result;
            }
            for (JsonElement element : items) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("id") || !object.has("block_state_b64")) {
                    continue;
                }
                String identifier = object.get("id").getAsString();
                if (result.containsKey(identifier)) {
                    continue;
                }
                BlockDefinition block = decodeBlockState(object, blocks);
                if (block != null) {
                    result.put(identifier, block);
                }
            }
        } catch (Exception ignored) {
            // Fall back to the newest mapping's block definition.
        }
        return result;
    }

    private static ItemMapping buildLodestone(Map<String, NeteaseItemFilter.ItemInfo> info,
                                              Map<String, ItemMapping> specialByIdentifier) {
        String identifier = "minecraft:lodestone_compass";
        NeteaseItemFilter.ItemInfo itemInfo = info.get(identifier);
        if (itemInfo == null || itemInfo.runtimeId() == Integer.MIN_VALUE) {
            return null;
        }
        ItemDefinition definition = new SimpleItemDefinition(identifier, itemInfo.runtimeId(),
            ItemVersion.from(itemInfo.version()), itemInfo.componentBased(), itemInfo.components());
        ItemMapping mapping = ItemMapping.builder()
            .javaItem(Items.COMPASS)
            .bedrockIdentifier(identifier)
            .bedrockDefinition(definition)
            .bedrockData(0)
            .bedrockBlockDefinition(null)
            .customItemDefinitions(null)
            .build();
        specialByIdentifier.put(identifier, mapping);
        return mapping;
    }

    private static Integer[] buildZeroBlockIds(ItemMapping[] items) {
        List<Integer> ids = new ArrayList<>();
        for (ItemMapping mapping : items) {
            if (mapping == null || !mapping.isBlock() || mapping.getBedrockBlockDefinition() == null) {
                continue;
            }
            if (mapping.getBedrockBlockDefinition().getRuntimeId() == 0) {
                ids.add(mapping.getBedrockDefinition().getRuntimeId());
            }
        }
        return ids.toArray(new Integer[0]);
    }

    private record CreativeData(List<CreativeItemGroup> groups, List<CreativeItemData> items) {
    }

    /**
     * Builds the creative tab from the NetEase client's own creative_items JSON. Using the
     * native list keeps group order, categories, icons, net ids and block states identical to
     * what the client expects.
     */
    private static CreativeData buildCreative(int protocolVersion,
                                              Map<String, NeteaseItemFilter.ItemInfo> info,
                                              BlockMappings blocks,
                                              Map<String, ItemMapping> byIdentifier,
                                              Map<String, ItemMapping> specialByIdentifier) {
        String resource = creativeResource(protocolVersion);
        try (InputStream stream = NeteaseItemMappings.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray groupsJson = root.getAsJsonArray("groups");
            JsonArray itemsJson = root.getAsJsonArray("items");
            if (groupsJson == null || itemsJson == null) {
                return null;
            }

            List<CreativeItemGroup> groups = new ArrayList<>(groupsJson.size());
            for (JsonElement element : groupsJson) {
                JsonObject group = element.getAsJsonObject();
                String name = group.has("name") ? group.get("name").getAsString() : "itemGroup.name.misc";
                CreativeItemCategory category = category(group.has("category")
                    ? group.get("category").getAsString() : "items");
                ItemData icon = null;
                JsonObject iconObject = group.getAsJsonObject("icon");
                if (iconObject != null && iconObject.has("id")) {
                    icon = buildCreativeItem(iconObject, info, blocks, byIdentifier,
                        specialByIdentifier, 0);
                }
                if (icon == null) {
                    icon = fallbackIcon(info);
                }
                groups.add(new CreativeItemGroup(category, name, icon));
            }

            List<CreativeItemData> items = new ArrayList<>(itemsJson.size());
            int netId = 1;
            for (JsonElement element : itemsJson) {
                JsonObject itemObject = element.getAsJsonObject();
                ItemData item = buildCreativeItem(itemObject, info, blocks, byIdentifier,
                    specialByIdentifier, netId);
                if (item == null) {
                    continue;
                }
                int groupId = itemObject.has("groupId") ? itemObject.get("groupId").getAsInt() : 0;
                items.add(new CreativeItemData(item, netId, groupId));
                netId++;
            }
            if (io.github.negeyser.NEGeyserExtension.LOG != null
                && io.github.negeyser.NEGeyserExtension.CONFIG != null
                && io.github.negeyser.NEGeyserExtension.CONFIG.debugMode()) {
                io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-CREATIVE] protocol "
                    + protocolVersion + " groups=" + groups.size() + " items=" + items.size());
            }
            return new CreativeData(groups, items);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemData buildCreativeItem(JsonObject object,
                                              Map<String, NeteaseItemFilter.ItemInfo> info,
                                              BlockMappings blocks,
                                              Map<String, ItemMapping> byIdentifier,
                                              Map<String, ItemMapping> specialByIdentifier,
                                              int netId) {
        if (!object.has("id")) {
            return null;
        }
        String identifier = object.get("id").getAsString();
        NeteaseItemFilter.ItemInfo itemInfo = info.get(identifier);
        if (itemInfo == null || itemInfo.runtimeId() == Integer.MIN_VALUE) {
            return null;
        }

        ItemData.Builder builder = ItemData.builder()
            .definition(definition(itemInfo, identifier))
            .count(object.has("count") ? object.get("count").getAsInt() : 1)
            .damage(object.has("damage") ? object.get("damage").getAsInt() : 0)
            .netId(netId);

        BlockDefinition block = decodeBlockState(object, blocks);
        if (block == null) {
            ItemMapping mapping = byIdentifier.getOrDefault(identifier,
                specialByIdentifier.get(identifier));
            if (mapping != null) {
                block = mapping.getBedrockBlockDefinition();
            }
        }
        if (block != null) {
            builder.blockDefinition(block);
        }
        if (object.has("nbt_b64")) {
            NbtMap tag = decodeNbt(object.get("nbt_b64").getAsString());
            if (tag != null) {
                builder.tag(tag);
            }
        }
        return builder.build();
    }

    private static ItemDefinition definition(NeteaseItemFilter.ItemInfo info, String identifier) {
        return new SimpleItemDefinition(identifier, info.runtimeId(),
            ItemVersion.from(info.version()), info.componentBased(), info.components());
    }

    private static ItemData fallbackIcon(Map<String, NeteaseItemFilter.ItemInfo> info) {
        NeteaseItemFilter.ItemInfo paper = info.get("minecraft:paper");
        if (paper != null && paper.runtimeId() != Integer.MIN_VALUE) {
            return ItemData.builder().definition(definition(paper, "minecraft:paper"))
                .count(1).build();
        }
        return ItemData.AIR;
    }

    private static BlockDefinition decodeBlockState(JsonObject object, BlockMappings blocks) {
        if (!object.has("block_state_b64")) {
            return null;
        }
        NbtMap state = decodeNbt(object.get("block_state_b64").getAsString());
        if (state == null) {
            return null;
        }
        // BlockMappings strips these metadata fields when loading its palette.
        NbtMapBuilder builder = state.toBuilder();
        builder.remove("name_hash");
        builder.remove("network_id");
        builder.remove("version");
        builder.remove("block_id");
        GeyserBedrockBlock unpatched = blocks.getDefinition(builder.build());
        if (unpatched == null) {
            return null;
        }
        GeyserBedrockBlock patched = blocks.getDefinition(unpatched.getRuntimeId());
        return patched != null ? patched : unpatched;
    }

    private static NbtMap decodeNbt(String base64) {
        try (InputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(base64))) {
            Object tag = NbtUtils.createReaderLE(stream).readTag();
            return tag instanceof NbtMap map ? map : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CreativeItemCategory category(String value) {
        return switch (value) {
            case "construction" -> CreativeItemCategory.CONSTRUCTION;
            case "nature" -> CreativeItemCategory.NATURE;
            case "equipment" -> CreativeItemCategory.EQUIPMENT;
            case "items" -> CreativeItemCategory.ITEMS;
            default -> CreativeItemCategory.ITEMS;
        };
    }

    private static BlockDefinition remapBlock(BlockDefinition definition, BlockMappings blocks) {
        if (definition instanceof GeyserBedrockBlock block) {
            GeyserBedrockBlock mapped = blocks.getDefinition(block.getRuntimeId());
            if (mapped != null) {
                return mapped;
            }
        }
        return definition;
    }

    private static Map<Integer, ItemDefinition> newDefinitionMap(ItemMappings newest) {
        try {
            // Read the field via Unsafe: Geyser-Spigot relocates fastutil, so the getter's
            // return type (Int2ObjectMap) would not match our compile-time class.
            return newMap(readField(newest, "itemDefinitions"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create a NetEase item definition map", e);
        }
    }

    private static Map<Integer, ItemMapping> newLightBlockMap(ItemMappings newest) {
        try {
            return newMap(readField(newest, "lightBlocks"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create a NetEase light block map", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<Integer, T> newMap(Object template) throws ReflectiveOperationException {
        return (Map<Integer, T>) template.getClass().getDeclaredConstructor().newInstance();
    }

    private static Object readField(ItemMappings instance, String name) throws NoSuchFieldException {
        Field field = ItemMappings.class.getDeclaredField(name);
        long offset = UnsafeHolder.UNSAFE.objectFieldOffset(field);
        return UnsafeHolder.UNSAFE.getObject(instance, offset);
    }

    private static ItemMappings copy(ItemMappings newest, ItemMapping[] items,
                                     Map<Integer, ItemDefinition> itemDefinitions,
                                     Map<Integer, ItemMapping> lightBlocks, ItemMapping lodestone,
                                     List<CreativeItemGroup> creativeItemGroups,
                                     List<CreativeItemData> creativeItems, StoredItemMappings storedItems,
                                     List<ItemDefinition> buckets, Integer[] zeroBlockDefinitionRuntimeId) {
        try {
            ItemMappings copy = (ItemMappings) UnsafeHolder.UNSAFE.allocateInstance(ItemMappings.class);
            for (Field field : ItemMappings.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                long offset = UnsafeHolder.UNSAFE.objectFieldOffset(field);
                UnsafeHolder.UNSAFE.putObject(copy, offset,
                    UnsafeHolder.UNSAFE.getObject(newest, offset));
            }
            set(copy, "items", items);
            set(copy, "itemDefinitions", itemDefinitions);
            set(copy, "lightBlocks", lightBlocks);
            set(copy, "lodestoneCompass", lodestone);
            set(copy, "creativeItemGroups", creativeItemGroups);
            set(copy, "creativeItems", creativeItems);
            set(copy, "storedItems", storedItems);
            set(copy, "buckets", buckets);
            set(copy, "boats", new ArrayList<ItemDefinition>());
            set(copy, "zeroBlockDefinitionRuntimeId", zeroBlockDefinitionRuntimeId);
            set(copy, "cachedJavaMappings", new WeakHashMap<String, ItemMapping>());
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create NetEase item mappings", e);
        }
    }

    private static void set(Object instance, String name, Object value) throws NoSuchFieldException {
        Field field = ItemMappings.class.getDeclaredField(name);
        long offset = UnsafeHolder.UNSAFE.objectFieldOffset(field);
        UnsafeHolder.UNSAFE.putObject(instance, offset, value);
    }

    private static final class UnsafeHolder {
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
    }
}
