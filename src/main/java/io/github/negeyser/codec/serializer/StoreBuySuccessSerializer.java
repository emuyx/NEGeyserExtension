/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.github.negeyser.codec.packet.StoreBuySuccessPacket;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;

public final class StoreBuySuccessSerializer implements BedrockPacketSerializer<StoreBuySuccessPacket> {

    public static final StoreBuySuccessSerializer INSTANCE = new StoreBuySuccessSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, StoreBuySuccessPacket packet) {
        // Not used by the bridge.
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, StoreBuySuccessPacket packet) {
        buffer.skipBytes(buffer.readableBytes());
    }

    private StoreBuySuccessSerializer() {
    }
}
