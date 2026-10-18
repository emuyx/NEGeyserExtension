/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.NEGeyserExtension;
import io.github.negeyser.config.NeteaseConfig;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Central translation of outbound item stacks to the NetEase item registry.
 *
 * <p>This is the single place that decides how a Geyser (26.x) item stack looks on the wire for
 * a NetEase client: which item definition/component NBT to use, which block definition to attach,
 * and which stack NBT is safe to keep. Every packet carrying {@code ItemData} should go through
 * this class instead of having its own ad-hoc remapping.</p>
 */
final class NeteaseItemTranslator {

    private final int protocolVersion;
    private final Set<String> knownItems;
    private final ItemDefinition placeholder;
    private final UnaryOperator<BlockDefinition> blockWrapper;
    private final Map<String, String> itemFallbacks;
    private final String locale;
    // SpoofedUpstream may be called from both the Bedrock event loop and Java packet threads.
    private final Map<String, ItemDefinition> rebuiltDefinitions = new ConcurrentHashMap<>();

    NeteaseItemTranslator(int protocolVersion, Set<String> knownItems, ItemDefinition placeholder,
                          UnaryOperator<BlockDefinition> blockWrapper,
                          Map<String, String> itemFallbacks, String locale) {
        this.protocolVersion = protocolVersion;
        this.knownItems = knownItems;
        this.placeholder = placeholder;
        this.blockWrapper = blockWrapper;
        this.itemFallbacks = itemFallbacks;
        this.locale = locale == null ? "en_us" : locale;
    }

    boolean isKnown(ItemDefinition definition) {
        return definition != null && definition.getIdentifier() != null
            && knownItems.contains(definition.getIdentifier());
    }

    /**
     * Rebuilds the definition using the NetEase client's version/components while keeping the
     * runtime id Geyser assigned (all other outbound packets still reference that id). Returns
     * {@code null} when the item does not exist in the NetEase registry.
     */
    ItemDefinition rebuiltDefinition(ItemDefinition original) {
        if (original == null || original.getIdentifier() == null) {
            return null;
        }
        return rebuiltDefinitions.computeIfAbsent(original.getIdentifier(), ignored -> {
            if (!knownItems.contains(original.getIdentifier())) {
                return null;
            }
            return NeteaseItemFilter.rebuild(original, protocolVersion);
        });
    }

    private ItemDefinition definitionFor(ItemDefinition original) {
        ItemDefinition rebuilt = rebuiltDefinition(original);
        return rebuilt != null ? rebuilt : placeholder;
    }

    /**
     * Translates an item stack for inventory/entity/creative packets, including the configured
     * item NBT policy. Unknown items become the placeholder item with their original identifier
     * as display name.
     */
    ItemData translate(ItemData item, boolean log) {
        return translate0(item, log, true);
    }

    /**
     * Translates only definitions and block ids, leaving stack NBT untouched. Used for recipe
     * results, where upstream Geyser already decides which component data is required.
     */
    ItemData translateDefinitionOnly(ItemData item) {
        return translate0(item, false, false);
    }

    private ItemData translate0(ItemData item, boolean log, boolean applyTagPolicy) {
        if (item == null || item.getDefinition() == null || placeholder == null) {
            return item;
        }
        String identifier = item.getDefinition().getIdentifier();
        if (isKnown(item.getDefinition())) {
            ItemDefinition definition = definitionFor(item.getDefinition());
            NbtMap tag = applyTagPolicy ? sanitizeTag(item.getTag()) : item.getTag();
            BlockDefinition block = wrap(item.getBlockDefinition());
            if (debug() && log) {
                NEGeyserExtension.LOG.info("[NETEASE-ITEM] known " + identifier
                    + " count=" + item.getCount() + " block=" + describeBlock(item.getBlockDefinition())
                    + " rawTag=" + item.getTag() + " tag=" + (tag == null ? "null" : tag.keySet()));
            }
            // Only the runtime id of a definition is serialized in item stacks; component data is
            // sent separately in ItemComponentPacket. Avoid rebuilding recipe results when the
            // wire representation did not change.
            if (definition.getRuntimeId() == item.getDefinition().getRuntimeId()
                && block == item.getBlockDefinition() && tag == item.getTag()) {
                return item;
            }
            return item.toBuilder()
                .definition(definition)
                .tag(tag)
                .blockDefinition(block)
                .build();
        }

        // Unknown item: prefer a similar NetEase item supplied by the upstream fallback table,
        // otherwise use the paper placeholder. Keep the original identifier as display name.
        String fallbackId = itemFallbacks.get(identifier);
        if (fallbackId == null) {
            fallbackId = NeteaseFallbacks.heuristicItemFallback(identifier, knownItems);
        }
        ItemDefinition fallbackDefinition = fallbackId == null ? null
            : NeteaseItemFilter.definition(fallbackId, protocolVersion);
        if (debug() && log) {
            NEGeyserExtension.LOG.info("[NETEASE-ITEM] unknown " + identifier
                + " count=" + item.getCount() + " block=" + describeBlock(item.getBlockDefinition())
                + " fallback=" + (fallbackId == null ? "paper" : fallbackId));
        }
        NbtMap display = NbtMap.builder()
            .putString("Name", "§r" + NeteaseFallbacks.displayName(identifier, locale))
            .build();
        NbtMap tag = NbtMap.builder().putCompound("display", display).build();
        return item.toBuilder()
            .definition(fallbackDefinition != null ? fallbackDefinition : placeholder)
            .tag(tag)
            .blockDefinition(wrap(item.getBlockDefinition()))
            .build();
    }

    private BlockDefinition wrap(BlockDefinition definition) {
        return definition == null ? null : blockWrapper.apply(definition);
    }

    private NbtMap sanitizeTag(NbtMap tag) {
        if (tag == null || tag.isEmpty()) {
            return tag;
        }
        NeteaseConfig config = NEGeyserExtension.CONFIG;
        if (config == null || !config.stripItemNbt()) {
            // Full pass-through. With native NetEase item definitions/components this is the
            // desired mode; keep raw Geyser NBT so books, dyed items, containers, trims and
            // other component data can be restored. GeyserHash is an internal Geyser marker
            // and is not needed by the client.
            if (tag.containsKey("GeyserHash")) {
                NbtMapBuilder builder = NbtMap.builder();
                for (Map.Entry<String, Object> entry : tag.entrySet()) {
                    if (!"GeyserHash".equals(entry.getKey())) {
                        builder.put(entry.getKey(), entry.getValue());
                    }
                }
                return builder.build();
            }
            return tag;
        }

        Set<String> extra = config.extraItemNbt();
        Set<String> keep = new HashSet<>();
        keep.add("display");
        keep.addAll(extra);
        NbtMapBuilder builder = NbtMap.builder();
        for (Map.Entry<String, Object> entry : tag.entrySet()) {
            if (keep.contains(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private static String describeBlock(BlockDefinition definition) {
        return definition == null ? "null" : Integer.toString(definition.getRuntimeId());
    }

    private static boolean debug() {
        return NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.debugMode();
    }
}
