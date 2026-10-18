/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.github.negeyser.codec.packet.SetDimensionLocalTimePacket;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.common.util.VarInts;

public final class SetDimensionLocalTimeSerializer implements BedrockPacketSerializer<SetDimensionLocalTimePacket> {

    public static final SetDimensionLocalTimeSerializer INSTANCE = new SetDimensionLocalTimeSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, SetDimensionLocalTimePacket packet) {
        if (!packet.isLocalTimeEnabled()) {
            return;
        }
        VarInts.writeInt(buffer, packet.getTime());
        buffer.writeBoolean(packet.isTickDayTime());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, SetDimensionLocalTimePacket packet) {
        if (!buffer.isReadable()) {
            packet.setLocalTimeEnabled(false);
            packet.setTime(0);
            packet.setTickDayTime(true);
            return;
        }
        packet.setLocalTimeEnabled(true);
        packet.setTime(VarInts.readInt(buffer));
        packet.setTickDayTime(buffer.readBoolean());
    }

    private SetDimensionLocalTimeSerializer() {
    }
}
