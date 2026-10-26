/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.common.util.TypeMap;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Translates Bedrock container slot types between the vanilla wire ids understood by the
 * Cloudburst codec and the ids used by NetEase clients.
 *
 * <p>NetEase inserts a {@code RECIPE_CUSTOM} value at the vanilla wire index of
 * {@code RECIPE_ITEMS} ({@code 17}), so every container at wire index 17 or above is shifted
 * up by one on the wire. This matches Nukkit-MOT's
 * {@code ContainerSlotType#getId(GameVersion)} / {@code fromId(int, GameVersion)} and is
 * required for more than the inventory/hotbar/offhand range: without it the cursor decodes as
 * {@code CREATED_OUTPUT}, loom/stonecutter/cartography containers decode as their successors,
 * and responses for those containers are sent with the wrong ids.</p>
 */
public final class NeteaseContainers {

    /** First shifted wire id: the NetEase-only RECIPE_CUSTOM replaces RECIPE_ITEMS here. */
    private static final int SHIFT_INDEX = 17;

    /**
     * Vanilla container slot types in wire order for protocols 819/860/898 (unchanged since
     * protocol 712). The enum declaration order is NOT the wire order.
     */
    private static final List<ContainerSlotType> WIRE_ORDER = List.of(
        ContainerSlotType.ANVIL_INPUT,
        ContainerSlotType.ANVIL_MATERIAL,
        ContainerSlotType.ANVIL_RESULT,
        ContainerSlotType.SMITHING_TABLE_INPUT,
        ContainerSlotType.SMITHING_TABLE_MATERIAL,
        ContainerSlotType.SMITHING_TABLE_RESULT,
        ContainerSlotType.ARMOR,
        ContainerSlotType.LEVEL_ENTITY,
        ContainerSlotType.BEACON_PAYMENT,
        ContainerSlotType.BREWING_INPUT,
        ContainerSlotType.BREWING_RESULT,
        ContainerSlotType.BREWING_FUEL,
        ContainerSlotType.HOTBAR_AND_INVENTORY,
        ContainerSlotType.CRAFTING_INPUT,
        ContainerSlotType.CRAFTING_OUTPUT,
        ContainerSlotType.RECIPE_CONSTRUCTION,
        ContainerSlotType.RECIPE_NATURE,
        ContainerSlotType.RECIPE_ITEMS,
        ContainerSlotType.RECIPE_SEARCH,
        ContainerSlotType.RECIPE_SEARCH_BAR,
        ContainerSlotType.RECIPE_EQUIPMENT,
        ContainerSlotType.RECIPE_BOOK,
        ContainerSlotType.ENCHANTING_INPUT,
        ContainerSlotType.ENCHANTING_MATERIAL,
        ContainerSlotType.FURNACE_FUEL,
        ContainerSlotType.FURNACE_INGREDIENT,
        ContainerSlotType.FURNACE_RESULT,
        ContainerSlotType.HORSE_EQUIP,
        ContainerSlotType.HOTBAR,
        ContainerSlotType.INVENTORY,
        ContainerSlotType.SHULKER_BOX,
        ContainerSlotType.TRADE_INGREDIENT_1,
        ContainerSlotType.TRADE_INGREDIENT_2,
        ContainerSlotType.TRADE_RESULT,
        ContainerSlotType.OFFHAND,
        ContainerSlotType.COMPOUND_CREATOR_INPUT,
        ContainerSlotType.COMPOUND_CREATOR_OUTPUT,
        ContainerSlotType.ELEMENT_CONSTRUCTOR_OUTPUT,
        ContainerSlotType.MATERIAL_REDUCER_INPUT,
        ContainerSlotType.MATERIAL_REDUCER_OUTPUT,
        ContainerSlotType.LAB_TABLE_INPUT,
        ContainerSlotType.LOOM_INPUT,
        ContainerSlotType.LOOM_DYE,
        ContainerSlotType.LOOM_MATERIAL,
        ContainerSlotType.LOOM_RESULT,
        ContainerSlotType.BLAST_FURNACE_INGREDIENT,
        ContainerSlotType.SMOKER_INGREDIENT,
        ContainerSlotType.TRADE2_INGREDIENT_1,
        ContainerSlotType.TRADE2_INGREDIENT_2,
        ContainerSlotType.TRADE2_RESULT,
        ContainerSlotType.GRINDSTONE_INPUT,
        ContainerSlotType.GRINDSTONE_ADDITIONAL,
        ContainerSlotType.GRINDSTONE_RESULT,
        ContainerSlotType.STONECUTTER_INPUT,
        ContainerSlotType.STONECUTTER_RESULT,
        ContainerSlotType.CARTOGRAPHY_INPUT,
        ContainerSlotType.CARTOGRAPHY_ADDITIONAL,
        ContainerSlotType.CARTOGRAPHY_RESULT,
        ContainerSlotType.BARREL,
        ContainerSlotType.CURSOR,
        ContainerSlotType.CREATED_OUTPUT,
        ContainerSlotType.SMITHING_TABLE_TEMPLATE,
        ContainerSlotType.CRAFTER_BLOCK_CONTAINER,
        ContainerSlotType.DYNAMIC_CONTAINER
    );

    private static final Map<ContainerSlotType, Integer> WIRE_IDS = new EnumMap<>(ContainerSlotType.class);

    private static volatile boolean installed;
    private static volatile boolean warned;

    static {
        for (int i = 0; i < WIRE_ORDER.size(); i++) {
            WIRE_IDS.put(WIRE_ORDER.get(i), i);
        }
    }

    private NeteaseContainers() {
    }

    /**
     * Replaces the codec helper's container slot TypeMap with one that speaks NetEase wire
     * ids directly, including wire id 64 ({@code DYNAMIC_CONTAINER}), which the vanilla map
     * does not contain and would throw on while decoding bundle interactions.
     *
     * <p>If this cannot be installed (for example because a future Geyser renamed the field),
     * the old post-decode/pre-encode shift helpers below are kept as a fallback.</p>
     */
    public static void install(BedrockCodecHelper helper) {
        try {
            TypeMap<ContainerSlotType> map = buildWireMap();
            setContainerSlotTypes(helper, map);
            boolean wasInstalled = installed;
            installed = true;
            if (!wasInstalled && io.github.negeyser.NEGeyserExtension.LOG != null) {
                io.github.negeyser.NEGeyserExtension.LOG.info(
                    "[NETEASE-CONTAINER] installed NetEase container slot ids (0-64)");
            }
        } catch (Throwable e) {
            // Keep a previous successful installation; only fall back if none worked.
            if (!installed && !warned && io.github.negeyser.NEGeyserExtension.LOG != null) {
                warned = true;
                io.github.negeyser.NEGeyserExtension.LOG.warning(
                    "Could not install the NetEase container slot map; falling back to "
                        + "post-decode translation: " + e.getMessage());
            }
        }
    }

    private static TypeMap<ContainerSlotType> buildWireMap() {
        TypeMap.Builder<ContainerSlotType> builder = TypeMap.builder(ContainerSlotType.class);
        // NetEase inserts RECIPE_CUSTOM at wire id 17; vanilla has no matching enum value.
        builder.insert(SHIFT_INDEX, ContainerSlotType.UNKNOWN);
        for (int i = 0; i < WIRE_ORDER.size(); i++) {
            int wireId = i >= SHIFT_INDEX ? i + 1 : i;
            builder.insert(wireId, WIRE_ORDER.get(i));
        }
        return builder.build();
    }

    private static void setContainerSlotTypes(BedrockCodecHelper helper,
                                              TypeMap<ContainerSlotType> map) throws Exception {
        Field field = null;
        for (Class<?> type = helper.getClass(); type != null; type = type.getSuperclass()) {
            try {
                field = type.getDeclaredField("containerSlotTypes");
                break;
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            }
        }
        if (field == null) {
            throw new NoSuchFieldException("containerSlotTypes");
        }
        UnsafeHolder.UNSAFE.putObject(helper,
            UnsafeHolder.UNSAFE.objectFieldOffset(field), map);
    }

    public static boolean isInstalled() {
        return installed;
    }

    /**
     * Maps a container decoded by the vanilla codec back to the container the NetEase client
     * meant. When the custom wire map is installed this is already handled by the codec.
     * Wire index 17 is the NetEase-only RECIPE_CUSTOM and is left unchanged.
     */
    public static ContainerSlotType fromWireDecoded(ContainerSlotType decoded) {
        if (installed) {
            return decoded;
        }
        Integer wireId = WIRE_IDS.get(decoded);
        if (wireId == null || wireId <= SHIFT_INDEX) {
            return decoded;
        }
        return WIRE_ORDER.get(wireId - 1);
    }

    /**
     * Maps a semantic container to the value the vanilla codec must encode so the NetEase
     * client decodes it as the intended container. No-op when the custom wire map is installed.
     */
    public static ContainerSlotType toWireDecoded(ContainerSlotType semantic) {
        if (installed) {
            return semantic;
        }
        Integer wireId = WIRE_IDS.get(semantic);
        if (wireId == null || wireId < SHIFT_INDEX || wireId + 1 >= WIRE_ORDER.size()) {
            return semantic;
        }
        return WIRE_ORDER.get(wireId + 1);
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
