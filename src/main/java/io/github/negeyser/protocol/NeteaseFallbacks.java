/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.network.bedrock.GameProtocol;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.text.MinecraftLocale;

import java.util.concurrent.CompletableFuture;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort translations for content that does not exist in the NetEase client version.
 *
 * <p>Upstream Geyser already contains a {@code WildernessBoundConverter} that maps newer
 * blocks/items to visually and mechanically similar older ones. Those mappings are reused here
 * (reflectively, as the tables are private), with a few NetEase-specific overrides (crafting
 * table/furnace use NetEase custom appearance states that are not hash-resolvable).</p>
 */
public final class NeteaseFallbacks {


    private static volatile Map<String, String> blockMappings;
    private static volatile Map<String, String> itemNames;
    private static final Map<Integer, Map<String, String>> itemMappings = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> localizedNames = new ConcurrentHashMap<>();

    /** Warm the locale tables off the session thread. Called once after Geyser initialized. */
    public static void preloadNames(String... locales) {
        CompletableFuture.runAsync(() -> {
            for (String locale : locales) {
                loadLocale(locale);
            }
        });
    }

    private static void loadLocale(String locale) {
        if (locale == null || locale.isEmpty() || localizedNames.containsKey(locale)
            && !localizedNames.get(locale).isEmpty()) {
            return;
        }
        Map<String, String> keys = itemNames();
        Map<String, String> names = new HashMap<>(keys.size());
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            try {
                String translated = MinecraftLocale.getLocaleString(entry.getValue(), locale);
                if (translated != null && !translated.isEmpty()
                    && !translated.equals(entry.getValue())) {
                    names.put(entry.getKey(), translated);
                }
            } catch (Throwable ignored) {
                // Missing locale entry; fall back to the title-cased identifier.
            }
        }
        localizedNames.put(locale, Collections.unmodifiableMap(names));
    }

    private static Map<String, String> itemNames() {
        Map<String, String> names = itemNames;
        if (names == null) {
            names = new HashMap<>();
            try {
                ItemMappings newest = Registries.ITEMS.forVersion(GameProtocol.DEFAULT_BEDROCK_PROTOCOL);
                for (ItemMapping mapping : newest.getItems()) {
                    if (mapping != null) {
                        names.put(mapping.getBedrockIdentifier(), mapping.getJavaItem().translationKey());
                    }
                }
            } catch (Throwable ignored) {
                // Empty map: displayName falls back to the identifier.
            }
            itemNames = names;
        }
        return names;
    }

    private NeteaseFallbacks() {
    }

    static String blockFallback(String name) {
        Map<String, String> mappings = blockMappings();
        String current = name;
        for (int depth = 0; depth < 4; depth++) {
            String next = mappings.get(current);
            if (next == null || next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    static Map<String, String> itemFallbacks(int protocolVersion) {
        return itemMappings.computeIfAbsent(protocolVersion, NeteaseFallbacks::buildItemFallbacks);
    }

    /**
     * Last-resort item substitution for content that neither the NetEase table nor the upstream
     * conversion table knows. Keeps the tool/armor class and picks the closest NetEase family.
     */
    static String heuristicItemFallback(String identifier, java.util.Set<String> knownItems) {
        String name = identifier.startsWith("minecraft:")
            ? identifier.substring("minecraft:".length()) : identifier;

        if (name.endsWith("_spear")) {
            return known("minecraft:trident", knownItems) ? "minecraft:trident"
                : known("minecraft:iron_sword", knownItems) ? "minecraft:iron_sword" : null;
        }
        if (name.startsWith("copper_")) {
            String iron = "minecraft:iron_" + name.substring("copper_".length());
            if (known(iron, knownItems)) {
                return iron;
            }
        }
        if (name.endsWith("_shelf")) {
            return known("minecraft:chiseled_bookshelf", knownItems)
                ? "minecraft:chiseled_bookshelf" : null;
        }
        if (name.endsWith("_cushion")) {
            String carpet = "minecraft:" + name.substring(0, name.length() - "_cushion".length()) + "_carpet";
            return known(carpet, knownItems) ? carpet : null;
        }
        if (name.endsWith("_chain")) {
            return known("minecraft:iron_chain", knownItems) ? "minecraft:iron_chain" : null;
        }
        if (name.endsWith("_bars")) {
            return known("minecraft:iron_bars", knownItems) ? "minecraft:iron_bars" : null;
        }
        if (name.endsWith("_chest")) {
            return known("minecraft:chest", knownItems) ? "minecraft:chest" : null;
        }
        if (name.endsWith("_torch")) {
            return known("minecraft:torch", knownItems) ? "minecraft:torch" : null;
        }
        if (name.endsWith("_lantern")) {
            return known("minecraft:lantern", knownItems) ? "minecraft:lantern" : null;
        }
        return null;
    }

    /**
     * Human-readable name for an item that has no NetEase translation. Prefers Geyser's Java
     * locale table (e.g. item.minecraft.copper_spear -> "Copper Spear"), then a title-cased id.
     */
    static String displayName(String identifier, String locale) {
        // Never touch MinecraftLocale on the session thread: the first lookup can load locale
        // resources and previously stalled logins. Names are preloaded in the background at
        // startup (see preloadNames) and only looked up from memory here.
        Map<String, String> names = localizedNames.get(locale);
        if (names == null && locale != null && !locale.isEmpty()) {
            names = localizedNames.computeIfAbsent(locale, ignored -> {
                CompletableFuture.runAsync(() -> loadLocale(locale));
                return Collections.emptyMap();
            });
        }
        String translated = names == null ? null : names.get(identifier);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }
        String name = identifier.contains(":")
            ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
        StringBuilder result = new StringBuilder(name.length());
        for (String part : name.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? identifier : result.toString();
    }

    private static boolean known(String identifier, java.util.Set<String> knownItems) {
        return knownItems.contains(identifier);
    }

    private static Map<String, String> buildItemFallbacks(int protocolVersion) {
        Map<Item, Item> mappings = itemMappings();
        if (mappings.isEmpty()) {
            return Collections.emptyMap();
        }
        ItemMappings newest = Registries.ITEMS.forVersion(GameProtocol.DEFAULT_BEDROCK_PROTOCOL);
        Map<String, NeteaseItemFilter.ItemInfo> known = NeteaseItemFilter.info(protocolVersion);
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<Item, Item> entry : mappings.entrySet()) {
            ItemMapping source = newest.getMapping(entry.getKey());
            ItemMapping target = newest.getMapping(entry.getValue());
            if (source == null || target == null) {
                continue;
            }
            String sourceId = source.getBedrockIdentifier();
            String targetId = target.getBedrockIdentifier();
            if (sourceId == null || targetId == null || known.containsKey(sourceId)
                || !known.containsKey(targetId)) {
                continue;
            }
            result.put(sourceId, targetId);
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> blockMappings() {
        Map<String, String> mappings = blockMappings;
        if (mappings == null) {
            try {
                Field field = Class.forName(
                        "org.geysermc.geyser.registry.populator.conversion.WildernessBoundConverter")
                    .getDeclaredField("BLOCK_MAPPINGS");
                field.setAccessible(true);
                mappings = (Map<String, String>) field.get(null);
            } catch (Throwable ignored) {
                mappings = Collections.emptyMap();
            }
            blockMappings = mappings;
        }
        return mappings;
    }

    @SuppressWarnings("unchecked")
    private static Map<Item, Item> itemMappings() {
        try {
            Field field = Class.forName(
                    "org.geysermc.geyser.registry.populator.conversion.WildernessBoundConverter")
                .getDeclaredField("ITEM_MAPPINGS");
            field.setAccessible(true);
            return (Map<Item, Item>) field.get(null);
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }
}
