/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.ResourcePackStackSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;

/**
 * NetEase 1.21.130 resource pack stack: vanilla 1.21.130 layout plus two NetEase VIP pack lists.
 */
public final class NeteaseResourcePackStackSerializer extends ResourcePackStackSerializer_v898 {

    public static final NeteaseResourcePackStackSerializer INSTANCE = new NeteaseResourcePackStackSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
        // 1.21.130 has one ordinary stack. Merge behaviour and resource packs without mutating
        // the packet.
        ArrayList<ResourcePackStackPacket.Entry> packs = new ArrayList<>(
            packet.getBehaviorPacks().size() + packet.getResourcePacks().size());
        packs.addAll(packet.getBehaviorPacks());
        packs.addAll(packet.getResourcePacks());

        buffer.writeBoolean(packet.isForcedToAccept());
        helper.writeArray(buffer, packs, this::writeEntry);
        helper.writeString(buffer, packet.getGameVersion());
        helper.writeExperiments(buffer, packet.getExperiments());
        buffer.writeBoolean(packet.isExperimentsPreviouslyToggled());
        buffer.writeBoolean(packet.isHasEditorPacks());

        // VIP resource pack and behaviour pack lists.
        VarInts.writeUnsignedInt(buffer, 0);
        VarInts.writeUnsignedInt(buffer, 0);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ResourcePackStackPacket packet) {
        super.deserialize(buffer, helper, packet);
        readVipPacks(buffer, helper);
        readVipPacks(buffer, helper);
    }

    private void readVipPacks(ByteBuf buffer, BedrockCodecHelper helper) {
        if (!buffer.isReadable()) {
            return;
        }
        helper.readArray(buffer, new ArrayList<ResourcePackStackPacket.Entry>(), this::readEntry);
    }

    private NeteaseResourcePackStackSerializer() {
    }
}
