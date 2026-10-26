/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.codec.packet.ConfirmSkinPacket;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityProperties;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.UpstreamSession;

import java.util.concurrent.TimeUnit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session upstream wrapper.
 *
 * <p>Two jobs:</p>
 * <ul>
 *     <li>Report the session's real Bedrock protocol version so Geyser selects the NetEase
 *     registries and version-gated packet content.</li>
 *     <li>Rewrite outgoing block ids to persistent hashes, translate/fallback items, and add
 *     the NetEase skin confirmation packets.</li>
 * </ul>
 */
public final class SpoofedUpstream extends UpstreamSession {

    private final GeyserSession session;
    private final UpstreamSession real;
    private final BlockMappings mappings;
    private final Set<GeyserBedrockBlock> patchedBlocks;
    private final HashAwareBlockRegistry blockRegistry;
    private final Set<String> knownItems;
    private final Set<String> sentRecipeIds = ConcurrentHashMap.newKeySet();
    private final NeteaseItemTranslator itemTranslator;
    private final int actualProtocol;
    private final Set<String> knownBiomes;
    private final ItemDefinition placeholderItem;
    private final String uid;
    private boolean skinConfirmed;

    /**
     * Player-list entries whose skin we have already confirmed to the client. NetEase 860
     * hides an actor when the same ADD+Confirm pair is sent twice without a preceding REMOVE,
     * so duplicates are suppressed and changes are sent as REMOVE -> ADD -> Confirm.
     */
    private final Map<UUID, SerializedSkin> confirmedPlayerListSkins = new ConcurrentHashMap<>();
    /** runtime entity id -> original Java entity identifier, for metadata fallbacks. */
    private final Map<Long, String> entityFallbacks = new ConcurrentHashMap<>();
    /** runtime entity id -> real name to show when a fallback entity has no name tag. */
    private final Map<Long, String> entityFallbackNames = new ConcurrentHashMap<>();
    /** Target codec's metadata type map, used to drop types the NetEase client cannot decode. */
    private final EntityDataTypeMap entityDataTypeMap;
    /** Set once the Java server's locator_bar gamerule has been read for this session. */
    private volatile boolean locatorBarResolved;

    public SpoofedUpstream(GeyserSession session, UpstreamSession real, BlockMappings mappings, String uid) {
        super(real.getSession());
        this.session = session;
        this.real = real;
        this.mappings = mappings;
        this.patchedBlocks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GeyserBedrockBlock block : mappings.getBedrockRuntimeMap()) {
            if (block != null) {
                patchedBlocks.add(block);
            }
        }
        this.blockRegistry = new HashAwareBlockRegistry(mappings);
        BedrockCodec codec = real.getSession().getCodec();
        this.actualProtocol = codec == null ? 898 : codec.getProtocolVersion();
        this.knownItems = NeteaseItemFilter.knownItems(actualProtocol);
        this.knownBiomes = NeteaseBiomes.knownBiomes();
        ItemDefinition paper = session.getItemMappings() == null ? null
            : session.getItemMappings().getDefinition("minecraft:paper");
        this.placeholderItem = paper != null ? paper
            : (session.getItemMappings() == null ? null : session.getItemMappings().getDefinition("minecraft:stone"));
        this.itemTranslator = new NeteaseItemTranslator(actualProtocol, knownItems, placeholderItem,
            this::wrap, NeteaseFallbacks.itemFallbacks(actualProtocol), session.locale());
        this.uid = uid;
        this.entityDataTypeMap = findEntityDataTypeMap(real.getCodecHelper());
    }

    @Override
    public int getProtocolVersion() {
        return actualProtocol;
    }

    @Override
    public void sendPacket(BedrockPacket packet) {
        if (!supported(packet)) {
            return;
        }
        BedrockPacket prepared = preparePlayerList(packet, false);
        if (prepared == null) {
            return;
        }
        BedrockPacket rewritten = rewrite(prepared);
        if (rewritten == null) {
            return;
        }
        sanitizeEntityPacket(rewritten);
        real.sendPacket(rewritten);
        afterSend(rewritten, false);
    }

    @Override
    public void sendPacketImmediately(BedrockPacket packet) {
        if (!supported(packet)) {
            return;
        }
        BedrockPacket prepared = preparePlayerList(packet, true);
        if (prepared == null) {
            return;
        }
        BedrockPacket rewritten = rewrite(prepared);
        if (rewritten == null) {
            return;
        }
        sanitizeEntityPacket(rewritten);
        real.sendPacketImmediately(rewritten);
        afterSend(rewritten, true);
    }

    /**
     * Geyser's newest internals may emit packets that did not exist in the NetEase client's
     * protocol (for example {@code VoxelShapesPacket}). They cannot be encoded by the NetEase
     * codec, so they are dropped instead of failing the whole session.
     */
    private boolean supported(BedrockPacket packet) {
        // Entity properties are a newer Bedrock feature; the 26.x property descriptors can make
        // the NetEase client fail while parsing them. Vanilla gameplay does not require them.
        if (packet instanceof SyncEntityPropertyPacket) {
            return false;
        }
        BedrockCodec codec = real.getSession().getCodec();
        if (codec == null) {
            return true;
        }
        //noinspection unchecked,rawtypes
        if (codec.getPacketDefinition((Class) packet.getClass()) == null) {
            if (packet instanceof io.netty.util.ReferenceCounted counted && counted.refCnt() > 0) {
                counted.release();
            }
            return false;
        }

        // Newer sound/event enum values are not in the target codec's TypeMap and would throw
        // while encoding. Dropping the individual packet is safer than killing the session.
        if (packet instanceof LevelSoundEventPacket sound
            && !supportsEvent(codec, packet, sound.getSound())) {
            if (debug()) {
                io.github.negeyser.NEGeyserExtension.LOG.info(
                    "[NETEASE-PACKET] dropping unsupported sound " + sound.getSound());
            }
            return false;
        }
        if (packet instanceof LevelEventPacket event
            && !supportsEvent(codec, packet, event.getType())) {
            if (debug()) {
                io.github.negeyser.NEGeyserExtension.LOG.info(
                    "[NETEASE-PACKET] dropping unsupported level event " + event.getType());
            }
            return false;
        }
        if (packet instanceof EntityEventPacket event
            && !supportsEvent(codec, packet, event.getType())) {
            if (debug()) {
                io.github.negeyser.NEGeyserExtension.LOG.info(
                    "[NETEASE-PACKET] dropping unsupported entity event " + event.getType());
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean supportsEvent(BedrockCodec codec, BedrockPacket packet, Object value) {
        BedrockPacketDefinition<?> definition =
            codec.getPacketDefinition((Class) packet.getClass());
        if (definition == null) {
            return false;
        }
        TypeMap<?> typeMap = findTypeMap(definition.getSerializer());
        if (typeMap == null) {
            return true;
        }
        try {
            return ((TypeMap) typeMap).getIdUnsafe(value) != -1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TypeMap<?> findTypeMap(Object serializer) {
        if (serializer == null) {
            return null;
        }
        for (Class<?> type = serializer.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("typeMap");
                field.setAccessible(true);
                Object value = field.get(serializer);
                return value instanceof TypeMap<?> map ? map : null;
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static EntityDataTypeMap findEntityDataTypeMap(BedrockCodecHelper helper) {
        if (helper == null) {
            return null;
        }
        for (Class<?> type = helper.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("entityData");
                field.setAccessible(true);
                Object value = field.get(helper);
                return value instanceof EntityDataTypeMap map ? map : null;
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Removes entity metadata types the target codec does not know, and clears entity properties
     * whose SyncEntityProperty definitions are dropped for NetEase clients.
     */
    private void sanitizeEntityPacket(BedrockPacket packet) {
        if (packet instanceof AddEntityPacket add) {
            removeUnsupportedMetadata(add.getMetadata());
            clearProperties(add.getProperties());
        } else if (packet instanceof SetEntityDataPacket data) {
            removeUnsupportedMetadata(data.getMetadata());
            clearProperties(data.getProperties());
        } else if (packet instanceof AddPlayerPacket player) {
            removeUnsupportedMetadata(player.getMetadata());
        }
    }

    private void removeUnsupportedMetadata(EntityDataMap metadata) {
        EntityDataTypeMap map = entityDataTypeMap;
        if (map == null || metadata == null || metadata.isEmpty()) {
            return;
        }
        int before = metadata.size();
        metadata.entrySet().removeIf(entry -> map.fromType(entry.getKey()) == null);
        int removed = before - metadata.size();
        if (removed > 0 && debug()) {
            io.github.negeyser.NEGeyserExtension.LOG.info(
                "[NETEASE-ENTITY] removed " + removed + " unsupported metadata type(s)");
        }
    }

    @Override
    public void disconnect(String reason) {
        real.disconnect(reason);
    }

    @Override
    public boolean isClosed() {
        return real.isClosed();
    }

    /**
     * NetEase 860 hides an actor when the same player-list entry is added and confirmed twice
     * without a preceding REMOVE. Geyser sends a player-list ADD whenever a skin is loaded or
     * refreshed (for example when a distant player enters view), so duplicate entries must be
     * suppressed and skin changes replaced explicitly.
     */
    private BedrockPacket preparePlayerList(BedrockPacket packet, boolean immediate) {
        // This is the known NetEase 860 client behaviour; other revisions are left untouched.
        if (actualProtocol != 860 || !(packet instanceof PlayerListPacket list)) {
            return packet;
        }

        if (list.getAction() == PlayerListPacket.Action.REMOVE) {
            for (PlayerListPacket.Entry entry : list.getEntries()) {
                confirmedPlayerListSkins.remove(entry.getUuid());
            }
            return packet;
        }
        if (list.getAction() != PlayerListPacket.Action.ADD) {
            return packet;
        }

        List<PlayerListPacket.Entry> entries = list.getEntries();
        List<PlayerListPacket.Entry> kept = new ArrayList<>(entries.size());
        boolean changed = false;
        for (PlayerListPacket.Entry entry : entries) {
            SerializedSkin skin = entry.getSkin();
            SerializedSkin previous = skin == null ? null : confirmedPlayerListSkins.get(entry.getUuid());
            if (previous != null && previous.equals(skin)) {
                changed = true;
                if (debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info(
                        "[NETEASE-SKIN] suppressing duplicate player-list entry "
                            + entry.getUuid());
                }
                continue;
            }

            if (previous != null) {
                // A different skin: reset the client's confirmed entry first.
                PlayerListPacket remove = new PlayerListPacket();
                remove.setAction(PlayerListPacket.Action.REMOVE);
                remove.getEntries().add(new PlayerListPacket.Entry(entry.getUuid()));
                if (immediate) {
                    real.sendPacketImmediately(remove);
                } else {
                    real.sendPacket(remove);
                }
                confirmedPlayerListSkins.remove(entry.getUuid());
                if (debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info(
                        "[NETEASE-SKIN] replacing player-list skin for " + entry.getUuid());
                }
            }
            kept.add(entry);
        }

        if (!changed) {
            return packet;
        }
        if (kept.isEmpty()) {
            return null;
        }
        entries.clear();
        entries.addAll(kept);
        return packet;
    }

    private BedrockPacket rewrite(BedrockPacket packet) {
        if (packet instanceof UpdateBlockPacket updateBlock) {
            updateBlock.setDefinition(wrap(updateBlock.getDefinition()));
            if (debug()) {
                BlockDefinition definition = updateBlock.getDefinition();
                NbtMap state = definition instanceof GeyserBedrockBlock block ? block.getState() : null;
                io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-BLOCK] pos="
                    + updateBlock.getBlockPosition() + " id=" + definition.getRuntimeId()
                    + " name=" + (state == null ? "?" : state.getString("name", "?"))
                    + " states=" + (state == null ? "?" : state.getCompound("states")));
            }
        } else if (packet instanceof UpdateSubChunkBlocksPacket update) {
            update.getStandardBlocks().replaceAll(this::rewriteBlockChange);
            update.getExtraBlocks().replaceAll(this::rewriteBlockChange);
        } else if (packet instanceof StartGamePacket startGame) {
            rewriteStartGame(startGame);
        } else if (packet instanceof GameRulesChangedPacket gameRules) {
            rewriteLocatorBar(gameRules);
        } else if (packet instanceof BiomeDefinitionListPacket biomePacket) {
            filterBiomes(biomePacket);
        } else if (packet instanceof AvailableEntityIdentifiersPacket entityIdentifiers) {
            NbtMap own = NeteaseEntities.identifiers();
            if (own != null && !own.isEmpty()) {
                entityIdentifiers.setIdentifiers(own);
            }
        } else if (packet instanceof CraftingDataPacket craftingData) {
            if (filterItems()) {
                filterRecipes(craftingData);
            }
        } else if (packet instanceof UnlockedRecipesPacket unlockedRecipes) {
            if (filterItems() && !filterUnlockedRecipes(unlockedRecipes)) {
                return null;
            }
        } else if (packet instanceof AddEntityPacket addEntity) {
            rewriteAddEntity(addEntity);
        } else if (packet instanceof SetEntityDataPacket entityData) {
            rewriteSetEntityData(entityData);
        } else if (packet instanceof ItemStackResponsePacket responses) {
            remapResponseContainers(responses);
            if (debug()) {
                for (ItemStackResponse response : responses.getEntries()) {
                    StringBuilder message = new StringBuilder("[NETEASE-RESP] id=")
                        .append(response.getRequestId()).append(" result=").append(response.getResult());
                    if (response.getContainers() != null) {
                        for (ItemStackResponseContainer container : response.getContainers()) {
                            message.append(" [").append(container.getContainer()).append(':');
                            for (org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot entry
                                : container.getItems()) {
                                message.append(" slot=").append(entry.getSlot())
                                    .append("/count=").append(entry.getCount())
                                    .append("/net=").append(entry.getStackNetworkId());
                            }
                            message.append(']');
                        }
                    }
                    io.github.negeyser.NEGeyserExtension.LOG.info(message.toString());
                }
            }
        } else if (filterItems()) {
            if (packet instanceof ItemComponentPacket components) {
                List<ItemDefinition> rebuilt = new ArrayList<>(components.getItems().size());
                for (ItemDefinition definition : components.getItems()) {
                    if (definition == null) {
                        continue;
                    }
                    ItemDefinition replacement = itemTranslator.rebuiltDefinition(definition);
                    if (replacement != null) {
                        rebuilt.add(replacement);
                    }
                }
                components.getItems().clear();
                components.getItems().addAll(rebuilt);
            } else if (packet instanceof InventoryContentPacket content) {
                // Geyser may build this list with Collections.singletonList for single-container
                // updates, so replaceAll would throw and abort the whole inventory update.
                List<ItemData> translated = new ArrayList<>(content.getContents().size());
                for (ItemData item : content.getContents()) {
                    translated.add(remapItem(item));
                }
                content.setContents(translated);
                content.setStorageItem(remapItem(content.getStorageItem()));
                if (debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-OUT] content container="
                        + content.getContainerId() + " items=" + translated.size()
                        + " storage=" + describeItem(content.getStorageItem()));
                }
            } else if (packet instanceof InventorySlotPacket slot) {
                slot.setItem(remapItem(slot.getItem()));
                if (debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-OUT] slot container="
                        + slot.getContainerId() + " slot=" + slot.getSlot()
                        + " item=" + describeItem(slot.getItem())
                        + " storage=" + describeItem(slot.getStorageItem()));
                }
            } else if (packet instanceof MobEquipmentPacket equipment) {
                equipment.setItem(remapItem(equipment.getItem()));
            } else if (packet instanceof MobArmorEquipmentPacket armor) {
                armor.setHelmet(remapItem(armor.getHelmet()));
                armor.setChestplate(remapItem(armor.getChestplate()));
                armor.setLeggings(remapItem(armor.getLeggings()));
                armor.setBoots(remapItem(armor.getBoots()));
                armor.setBody(remapItem(armor.getBody()));
            } else if (packet instanceof AddItemEntityPacket itemEntity) {
                itemEntity.setItemInHand(remapItem(itemEntity.getItemInHand()));
            } else if (packet instanceof AddPlayerPacket addPlayer) {
                addPlayer.setHand(remapItem(addPlayer.getHand()));
            } else if (packet instanceof CreativeContentPacket creative) {
                List<CreativeItemData> replaced = new ArrayList<>(creative.getContents().size());
                for (CreativeItemData entry : creative.getContents()) {
                    ItemData remapped = entry == null ? null : remapItem(entry.getItem());
                    if (entry != null) {
                        replaced.add(remapped == entry.getItem()
                            ? entry
                            : new CreativeItemData(remapped, entry.getNetId(), entry.getGroupId()));
                    }
                }
                creative.getContents().clear();
                creative.getContents().addAll(replaced);
            }
        }
        return packet;
    }

    /**
     * NetEase shifts every container at or above RECIPE_ITEMS by one on the wire, so responses
     * must be encoded with the same shift. This includes the cursor and the high-value
     * containers (loom/stonecutter/cartography/result containers); missing them made clicks
     * involving those containers resolve against the wrong slot.
     */
    private static void remapResponseContainers(ItemStackResponsePacket packet) {
        for (ItemStackResponse response : packet.getEntries()) {
            List<ItemStackResponseContainer> containers = response.getContainers();
            if (containers == null) {
                continue;
            }
            for (int i = 0; i < containers.size(); i++) {
                ItemStackResponseContainer container = containers.get(i);
                ContainerSlotType type = container.getContainer();
                ContainerSlotType mapped = NeteaseContainers.toWireDecoded(type);
                if (mapped != type) {
                    containers.set(i, new ItemStackResponseContainer(mapped, container.getItems(),
                        new FullContainerName(mapped, container.getContainerName().getDynamicId())));
                }
            }
        }
    }

    private void filterBiomes(BiomeDefinitionListPacket packet) {
        if (knownBiomes.isEmpty() || packet.getBiomes() == null) {
            return;
        }
        Map<String, BiomeDefinitionData> filtered = new HashMap<>();
        for (Map.Entry<String, BiomeDefinitionData> entry : packet.getBiomes().getDefinitions().entrySet()) {
            if (knownBiomes.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        // If the naming scheme does not overlap at all, keep the original list rather than
        // sending an empty biome registry (which would break chunk decoding).
        if (filtered.isEmpty() && !packet.getBiomes().getDefinitions().isEmpty()) {
            return;
        }
        packet.setBiomes(new BiomeDefinitions(filtered));
    }

    private ItemData remapItem(ItemData item) {
        return itemTranslator.translate(item, true);
    }

    private static String describeItem(ItemData item) {
        if (item == null) {
            return "null";
        }
        if (item.getDefinition() == null) {
            return "nodef";
        }
        return item.getDefinition().getIdentifier() + "x" + item.getCount()
            + "/net=" + item.getNetId() + "/usingNet=" + item.isUsingNetId();
    }

    private boolean filterItems() {
        return !knownItems.isEmpty()
            && (io.github.negeyser.NEGeyserExtension.CONFIG == null
                || io.github.negeyser.NEGeyserExtension.CONFIG.filterUnknownItems());
    }

    /**
     * Removes recipes that reference items (or remapped blocks) the NetEase client does not have.
     * Recipe-book unlock packets are filtered against the ids that survived here.
     */
    private void filterRecipes(CraftingDataPacket packet) {
        NeteaseRecipeFilter.Result result = NeteaseRecipeFilter.filter(
            packet, itemTranslator, sentRecipeIds);
        if (debug()) {
            io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-RECIPE] crafting clean="
                + packet.isCleanRecipes() + " total=" + result.total() + " dropped=" + result.dropped()
                + (result.unknownItems().isEmpty() ? "" : " unknown=" + result.unknownItems())
                + (result.droppedIds().isEmpty() ? "" : " droppedIds=" + sample(result.droppedIds())));
        }
    }

    private boolean filterUnlockedRecipes(UnlockedRecipesPacket packet) {
        int before = packet.getUnlockedRecipes().size();
        NeteaseRecipeFilter.filterUnlocked(packet, sentRecipeIds);
        boolean empty = packet.getUnlockedRecipes().isEmpty()
            && packet.getAction() != UnlockedRecipesPacket.ActionType.REMOVE_ALL;
        if (debug()) {
            io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-RECIPE] unlocked action="
                + packet.getAction() + " total=" + before + " kept=" + packet.getUnlockedRecipes().size()
                + (empty ? " (packet dropped)" : ""));
        }
        return !empty;
    }

    private static String sample(Set<String> values) {
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (String value : values) {
            if (count == 8) {
                builder.append(", ...");
                break;
            }
            if (count++ > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.append(']').toString();
    }

    private static boolean debug() {
        return io.github.negeyser.NEGeyserExtension.CONFIG != null
            && io.github.negeyser.NEGeyserExtension.CONFIG.debugMode();
    }

    private BlockChangeEntry rewriteBlockChange(BlockChangeEntry entry) {
        BlockDefinition definition = wrap(entry.getDefinition());
        if (definition == entry.getDefinition()) {
            return entry;
        }
        return new BlockChangeEntry(entry.getPosition(), definition, entry.getUpdateFlags(),
            entry.getMessageEntityId(), entry.getMessageType());
    }

    /**
     * Java 26.x introduced entities (and 1.21.11 introduced others) that the NetEase client's
     * registry does not contain. Fall back to the closest vanilla entity and strip metadata
     * that is invalid for that entity.
     */
    private void rewriteAddEntity(AddEntityPacket packet) {
        String original = packet.getIdentifier();
        EntityDataMap metadata = packet.getMetadata();
        boolean boat = "minecraft:boat".equals(original) || "minecraft:chest_boat".equals(original);
        Integer boatVariant = boat ? metadata.get(EntityDataTypes.VARIANT) : null;
        boolean futureBoat = boatVariant != null && boatVariant >= 10;
        String mapped = fallbackIdentifier(original);

        if (!mapped.equals(original)) {
            packet.setIdentifier(mapped);
        }
        if (futureBoat || !mapped.equals(original)) {
            String realName = futureEntityName(original, boatVariant);
            entityFallbackNames.put(packet.getRuntimeEntityId(), realName);
            if (!hasName(metadata)) {
                applyFallbackName(metadata, realName);
            }
            applyEntityFallbackMetadata(original, metadata, packet.getProperties());
            entityFallbacks.put(packet.getRuntimeEntityId(), original);
            if (debug()) {
                io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-ENTITY] "
                    + original + " -> " + mapped + " name=" + realName
                    + " runtime=" + packet.getRuntimeEntityId());
            }
        }
    }

    private void rewriteSetEntityData(SetEntityDataPacket packet) {
        long runtimeId = packet.getRuntimeEntityId();
        String original = entityFallbacks.get(runtimeId);
        if (original == null) {
            return;
        }
        String realName = entityFallbackNames.get(runtimeId);
        if (realName != null && !hasName(packet.getMetadata())) {
            applyFallbackName(packet.getMetadata(), realName);
        }
        applyEntityFallbackMetadata(original, packet.getMetadata(), packet.getProperties());
    }

    /**
     * A fallback entity with a player-applied name tag keeps that name. Unnamed future entities
     * get a permanent nameplate with their real (original) name so they can be identified.
     */
    private static boolean hasName(EntityDataMap metadata) {
        Object name = metadata.get(EntityDataTypes.NAME);
        return name != null && !name.toString().isBlank();
    }

    private static void applyFallbackName(EntityDataMap metadata, String name) {
        metadata.put(EntityDataTypes.NAME, "§r" + name);
        metadata.put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
        metadata.setFlag(EntityFlag.CAN_SHOW_NAME, true);
    }

    private static String futureEntityName(String identifier, Integer boatVariant) {
        if ("minecraft:boat".equals(identifier) || "minecraft:chest_boat".equals(identifier)) {
            boolean chest = identifier.endsWith("chest_boat");
            if (boatVariant != null && boatVariant == 10) {
                return chest ? "Poplar Chest Boat" : "Poplar Boat";
            }
            return chest ? "Chest Boat" : "Boat";
        }

        String path = identifier.startsWith("minecraft:")
            ? identifier.substring("minecraft:".length()) : identifier;
        StringBuilder result = new StringBuilder(path.length());
        for (String part : path.split("_")) {
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

    private String fallbackIdentifier(String identifier) {
        return switch (identifier) {
            case "minecraft:cushion" -> "minecraft:falling_block";
            case "minecraft:sulfur_cube" -> "minecraft:slime";
            case "minecraft:nautilus" -> actualProtocol <= 860
                ? "minecraft:squid" : identifier;
            case "minecraft:zombie_nautilus" -> actualProtocol <= 860
                ? "minecraft:glow_squid" : identifier;
            case "minecraft:camel_husk" -> actualProtocol <= 860
                ? "minecraft:camel" : identifier;
            case "minecraft:parched" -> actualProtocol <= 860
                ? "minecraft:skeleton" : identifier;
            default -> identifier;
        };
    }

    private void applyEntityFallbackMetadata(String original, EntityDataMap metadata,
                                             EntityProperties properties) {
        switch (original) {
            case "minecraft:cushion" -> {
                // Falling blocks render the carpet we select; cushion dye color is not valid on it.
                metadata.remove(EntityDataTypes.VARIANT);
                BlockDefinition carpet = mappings.getBedrockBlock(Blocks.WHITE_CARPET.defaultBlockState());
                if (carpet != null) {
                    metadata.put(EntityDataTypes.BLOCK, carpet);
                }
            }
            case "minecraft:sulfur_cube" -> {
                metadata.remove(EntityDataTypes.FUSE_TIME);
                clearProperties(properties);
            }
            case "minecraft:nautilus", "minecraft:zombie_nautilus" -> {
                metadata.remove(EntityDataTypes.CONTAINER_SIZE);
                clearProperties(properties);
            }
            case "minecraft:camel_husk", "minecraft:parched" -> clearProperties(properties);
            case "minecraft:boat", "minecraft:chest_boat" -> clampBoatVariant(metadata);
            default -> {
                // No metadata changes.
            }
        }
    }

    private void clampBoatVariant(EntityDataMap metadata) {
        Integer variant = metadata.get(EntityDataTypes.VARIANT);
        if (variant == null) {
            return;
        }
        // POPLAR is ordinal 10. 1.21.130 knows PALE_OAK (9); older targets fall back to BIRCH (2).
        int maxVariant = actualProtocol >= 898 ? 9 : 2;
        if (variant > maxVariant) {
            metadata.put(EntityDataTypes.VARIANT, maxVariant);
        }
    }

    private static void clearProperties(EntityProperties properties) {
        if (properties == null) {
            return;
        }
        properties.getIntProperties().clear();
        properties.getFloatProperties().clear();
    }

    private BlockDefinition wrap(BlockDefinition definition) {
        if (!(definition instanceof GeyserBedrockBlock block) || block.getState() == null) {
            return definition;
        }
        if (patchedBlocks.contains(block)) {
            // Already a per-session block (including fallback states).
            return block;
        }
        int runtimeId = block.getRuntimeId();
        if (runtimeId < 0 || runtimeId >= mappings.getBedrockRuntimeMap().length) {
            return definition;
        }
        GeyserBedrockBlock mapped = mappings.getBedrockRuntimeMap()[runtimeId];
        return mapped != null ? mapped : definition;
    }

    /**
     * NetEase clients enable the locator bar by default. Upstream Geyser always sends
     * {@code locatorBar=true} because recent Bedrock clients only render it when waypoints
     * exist; NetEase 860 lacks that behaviour. Hide it until the Java server's
     * {@code locator_bar} gamerule has been read for this session.
     */
    private void rewriteLocatorBar(GameRulesChangedPacket packet) {
        if (locatorBarResolved) {
            return;
        }
        List<GameRuleData<?>> rules = packet.getGameRules();
        for (int i = 0; i < rules.size(); i++) {
            if ("locatorBar".equals(rules.get(i).getName())) {
                rules.set(i, new GameRuleData<>("locatorBar", false));
                return;
            }
        }
        rules.add(new GameRuleData<>("locatorBar", false));
    }

    /**
     * Applies the Java server's {@code locator_bar} gamerule to the NetEase client.
     */
    public void setLocatorBar(boolean value) {
        locatorBarResolved = true;
        GameRulesChangedPacket packet = new GameRulesChangedPacket();
        packet.getGameRules().add(new GameRuleData<>("locatorBar", value));
        real.sendPacket(packet);
        if (debug()) {
            io.github.negeyser.NEGeyserExtension.LOG.info(
                "[NETEASE-GAMERULE] locatorBar=" + value);
        }
    }

    private void rewriteStartGame(StartGamePacket startGame) {
        // Serverbound block ids from NetEase clients are hashes; make the codec helper able to
        // resolve them while still accepting Geyser's index-based definitions.
        real.getCodecHelper().setBlockDefinitions(blockRegistry);
        startGame.setBlockNetworkIdsHashed(true);
        startGame.setBlockRegistryChecksum(0L);
        // Block properties describe data-driven/custom blocks from the 26.x registry. NetEase
        // clients do not need them for vanilla blocks and newer property data can crash them.
        startGame.getBlockProperties().clear();
        // Player property NBT also comes from the 26.x entity-property registry.
        startGame.setPlayerPropertyData(NbtMap.EMPTY);
        // Do NOT synthesize a block palette from Geyser's newest palette: it contains block
        // states introduced after 1.21.130 and can crash the NetEase client while loading the
        // level. Geyser leaves the palette empty for modern clients; hashed ids are resolved
        // against the client's own registry.
    }

    private void afterSend(BedrockPacket packet, boolean immediate) {
        if (packet instanceof StartGamePacket && !skinConfirmed) {
            skinConfirmed = true;
            // No self confirmation: an empty skin image is rejected/can crash NetEase clients.
            // Only real PlayerList/PlayerSkin skins below are confirmed.
        } else if (packet instanceof PlayerSkinPacket skinPacket && skinPacket.getSkin() != null) {
            sendSkinConfirmation(skinPacket.getUuid(), skinPacket.getSkin(), immediate);
        } else if (packet instanceof PlayerListPacket list && list.getAction() == PlayerListPacket.Action.ADD) {
            for (PlayerListPacket.Entry entry : list.getEntries()) {
                if (entry.getSkin() != null
                    && sendSkinConfirmation(entry.getUuid(), entry.getSkin(), immediate)) {
                    confirmedPlayerListSkins.put(entry.getUuid(), entry.getSkin());
                }
            }
        }
        if (packet instanceof ItemStackResponsePacket) {
            // The NetEase client does not apply every incremental container-name response. Push the
            // authoritative inventory contents after each stack request so the UI cannot desync.
            session.scheduleInEventLoop(() -> {
                if (session.isClosed()) {
                    return;
                }
                InventoryHolder<?> holder = session.getInventoryHolder();
                if (holder == null) {
                    return;
                }
                if (debug()) {
                    io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-RESYNC] inventory="
                        + holder.javaId());
                }
                holder.updateInventory();
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    private boolean sendSkinConfirmation(UUID uuid, SerializedSkin skin, boolean immediate) {
        ImageData data = skin.getSkinData();
        byte[] bytes = data == null ? new byte[0] : data.getImage();
        if (bytes.length == 0) {
            return false;
        }
        String entryUid = String.valueOf(Math.abs(uuid.toString().replace("-", "").hashCode()));
        String geometry = skin.getGeometryData() == null ? "" : skin.getGeometryData();
        sendConfirmation(uuid, entryUid, bytes, geometry, immediate);
        return true;
    }

    private void sendConfirmation(UUID uuid, String entryUid, byte[] skinData, String geometry, boolean immediate) {
        ConfirmSkinPacket confirmation = new ConfirmSkinPacket();
        ConfirmSkinPacket.Entry entry = new ConfirmSkinPacket.Entry();
        entry.setValid(true);
        entry.setUuid(uuid);
        entry.setSkinData(skinData);
        entry.setUid(entryUid == null ? "" : entryUid);
        entry.setGeometry(geometry);
        confirmation.getEntries().add(entry);

        if (immediate) {
            real.sendPacketImmediately(confirmation);
        } else {
            real.sendPacket(confirmation);
        }
    }

    /**
     * Replaces the final {@code upstream} field of a session. The field is final, so this uses
     * Unsafe; the previous value is returned for diagnostics.
     */
    public static UpstreamSession inject(GeyserSession session, UpstreamSession replacement) {
        try {
            Field field = GeyserSession.class.getDeclaredField("upstream");
            field.setAccessible(true);
            UnsafeHolder.UNSAFE.putObject(session, UnsafeHolder.UNSAFE.objectFieldOffset(field), replacement);
            return replacement;
        } catch (Throwable e) {
            throw new IllegalStateException("Unable to install the NetEase upstream wrapper", e);
        }
    }

    private static final class UnsafeHolder {
        private static final sun.misc.Unsafe UNSAFE;

        static {
            try {
                Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                UNSAFE = (sun.misc.Unsafe) field.get(null);
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

}
