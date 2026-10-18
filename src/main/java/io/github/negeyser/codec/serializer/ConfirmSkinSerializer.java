/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import io.github.negeyser.codec.packet.ConfirmSkinPacket;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.List;

/**
 * NetEase skin confirmation. Layout (NetEase skin anti-cheat document 4.14):
 * entry count, then (valid, uuid, skin bytes) per entry, then uid strings, then geometry strings.
 */
public final class ConfirmSkinSerializer implements BedrockPacketSerializer<ConfirmSkinPacket> {

    public static final ConfirmSkinSerializer INSTANCE = new ConfirmSkinSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
        List<ConfirmSkinPacket.Entry> entries = packet.getEntries();
        VarInts.writeUnsignedInt(buffer, entries.size());

        for (ConfirmSkinPacket.Entry entry : entries) {
            buffer.writeBoolean(entry.isValid());
            helper.writeUuid(buffer, entry.getUuid());
            helper.writeByteArray(buffer, entry.getSkinData());
        }

        for (ConfirmSkinPacket.Entry entry : entries) {
            helper.writeString(buffer, entry.getUid());
        }

        for (ConfirmSkinPacket.Entry entry : entries) {
            helper.writeString(buffer, entry.getGeometry());
        }
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ConfirmSkinPacket packet) {
        int size = VarInts.readUnsignedInt(buffer);
        for (int i = 0; i < size; i++) {
            ConfirmSkinPacket.Entry entry = new ConfirmSkinPacket.Entry();
            entry.setValid(buffer.readBoolean());
            entry.setUuid(helper.readUuid(buffer));
            entry.setSkinData(helper.readByteArray(buffer));
            packet.getEntries().add(entry);
        }
        for (ConfirmSkinPacket.Entry entry : packet.getEntries()) {
            entry.setUid(helper.readString(buffer));
        }
        for (ConfirmSkinPacket.Entry entry : packet.getEntries()) {
            entry.setGeometry(helper.readString(buffer));
        }
    }

    private ConfirmSkinSerializer() {
    }
}
