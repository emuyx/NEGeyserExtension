/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.CommandRequestSerializer_v291;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

/**
 * 1.21.130 command requests carry an additional NetEase "unlimit" flag.
 */
public final class NeteaseCommandRequestSerializer extends CommandRequestSerializer_v291 {

    public static final NeteaseCommandRequestSerializer INSTANCE = new NeteaseCommandRequestSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, CommandRequestPacket packet) {
        super.serialize(buffer, helper, packet);
        buffer.writeBoolean(false); // NetEase unlimit
        helper.writeString(buffer, "latest");
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, CommandRequestPacket packet) {
        super.deserialize(buffer, helper, packet);
        buffer.readBoolean(); // unlimit does not change Java command permissions
        helper.readString(buffer);
        packet.setVersion(48); // Same version mapping as CommandRequestSerializer_v898
    }

    private NeteaseCommandRequestSerializer() {
    }
}
