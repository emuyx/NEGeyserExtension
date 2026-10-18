/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.ContainerOpenSerializer_v291;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;

/**
 * NetEase 1.21.124+ appends a boolean to container open packets.
 */
public final class NeteaseContainerOpenSerializer extends ContainerOpenSerializer_v291 {

    public static final NeteaseContainerOpenSerializer INSTANCE = new NeteaseContainerOpenSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, ContainerOpenPacket packet) {
        super.serialize(buffer, helper, packet);
        buffer.writeBoolean(false);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, ContainerOpenPacket packet) {
        super.deserialize(buffer, helper, packet);
        buffer.readBoolean();
    }

    private NeteaseContainerOpenSerializer() {
    }
}
