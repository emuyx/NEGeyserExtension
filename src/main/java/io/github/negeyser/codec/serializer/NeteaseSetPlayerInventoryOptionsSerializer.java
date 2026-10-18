/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v630.serializer.SetPlayerInventoryOptionsSerializer_v630;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * NetEase 1.21.124+ requires an extra inventory option field.
 */
public final class NeteaseSetPlayerInventoryOptionsSerializer extends SetPlayerInventoryOptionsSerializer_v630 {

    public static final NeteaseSetPlayerInventoryOptionsSerializer INSTANCE = new NeteaseSetPlayerInventoryOptionsSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SetPlayerInventoryOptionsPacket packet) {
        super.serialize(buffer, helper, packet);
        VarInts.writeUnsignedInt(buffer, 0);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SetPlayerInventoryOptionsPacket packet) {
        super.deserialize(buffer, helper, packet);
        if (buffer.isReadable()) {
            VarInts.readUnsignedInt(buffer);
        }
    }

    private NeteaseSetPlayerInventoryOptionsSerializer() {
    }
}
