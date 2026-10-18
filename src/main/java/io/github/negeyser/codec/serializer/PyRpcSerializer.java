/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.github.negeyser.codec.packet.PyRpcPacket;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;

public final class PyRpcSerializer implements BedrockPacketSerializer<PyRpcPacket> {

    public static final PyRpcSerializer INSTANCE = new PyRpcSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, PyRpcPacket packet) {
        // Not used by the bridge.
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, PyRpcPacket packet) {
        buffer.skipBytes(buffer.readableBytes());
    }

    private PyRpcSerializer() {
    }
}
