/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT license.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v407.serializer.PlayerEnchantOptionsSerializer_v407;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.List;

/**
 * NetEase adds a string field to every enchant entry and a custom-enchant list to each option.
 */
public final class NeteaseEnchantOptionsSerializer extends PlayerEnchantOptionsSerializer_v407 {

    public static final NeteaseEnchantOptionsSerializer INSTANCE = new NeteaseEnchantOptionsSerializer();

    @Override
    protected void writeOption(ByteBuf buffer, BedrockCodecHelper helper, EnchantOptionData option) {
        VarInts.writeUnsignedInt(buffer, option.getCost());
        buffer.writeIntLE(option.getPrimarySlot());
        helper.writeArray(buffer, option.getEnchants0(), (buf, enchant) -> writeEnchant(buf, helper, enchant));
        helper.writeArray(buffer, option.getEnchants1(), (buf, enchant) -> writeEnchant(buf, helper, enchant));
        helper.writeArray(buffer, option.getEnchants2(), (buf, enchant) -> writeEnchant(buf, helper, enchant));
        VarInts.writeUnsignedInt(buffer, 0);
        helper.writeString(buffer, option.getEnchantName());
        VarInts.writeUnsignedInt(buffer, option.getEnchantNetId());
    }

    @Override
    protected EnchantOptionData readOption(ByteBuf buffer, BedrockCodecHelper helper) {
        int cost = VarInts.readUnsignedInt(buffer);
        int primarySlot = buffer.readIntLE();

        List<EnchantData> enchants0 = new ArrayList<>();
        helper.readArray(buffer, enchants0, (buf, codecHelper) -> readEnchant(buf, codecHelper));
        List<EnchantData> enchants1 = new ArrayList<>();
        helper.readArray(buffer, enchants1, (buf, codecHelper) -> readEnchant(buf, codecHelper));
        List<EnchantData> enchants2 = new ArrayList<>();
        helper.readArray(buffer, enchants2, (buf, codecHelper) -> readEnchant(buf, codecHelper));

        int customCount = VarInts.readUnsignedInt(buffer);
        for (int i = 0; i < customCount; i++) {
            readEnchant(buffer, helper);
        }

        String enchantName = helper.readString(buffer);
        int enchantNetId = VarInts.readUnsignedInt(buffer);
        return new EnchantOptionData(cost, primarySlot, enchants0, enchants1, enchants2, enchantName, enchantNetId);
    }

    private static void writeEnchant(ByteBuf buffer, BedrockCodecHelper helper, EnchantData enchant) {
        buffer.writeByte(enchant.getType());
        buffer.writeByte(enchant.getLevel());
        helper.writeString(buffer, "");
    }

    private static EnchantData readEnchant(ByteBuf buffer, BedrockCodecHelper helper) {
        int type = buffer.readUnsignedByte();
        int level = buffer.readUnsignedByte();
        helper.readString(buffer);
        return new EnchantData(type, level);
    }

    private NeteaseEnchantOptionsSerializer() {
    }
}
