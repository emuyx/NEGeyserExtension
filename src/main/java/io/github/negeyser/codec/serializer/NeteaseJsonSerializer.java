/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import com.google.gson.JsonObject;
import io.github.negeyser.codec.packet.NeteaseJsonPacket;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;

public final class NeteaseJsonSerializer implements BedrockPacketSerializer<NeteaseJsonPacket> {

    public static final NeteaseJsonSerializer INSTANCE = new NeteaseJsonSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, NeteaseJsonPacket packet) {
        JsonObject payload = new JsonObject();
        payload.addProperty("entityId", packet.getEntityId());
        payload.addProperty("eventName", packet.getEvent().eventName());
        payload.addProperty(packet.getEvent().valueName(), packet.getValue());
        helper.writeString(buffer, payload.toString());
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, NeteaseJsonPacket packet) {
        // 0xCB is a general NetEase event channel. Events this bridge does not consume are
        // discarded so that unrelated client events cannot desynchronise the packet stream.
        buffer.skipBytes(buffer.readableBytes());
    }

    private NeteaseJsonSerializer() {
    }
}
