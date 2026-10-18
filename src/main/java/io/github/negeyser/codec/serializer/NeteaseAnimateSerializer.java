/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.AnimateSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.common.util.VarInts;

/**
 * NetEase 1.21.130 appends the attacking actor's unique id to animate packets.
 */
public final class NeteaseAnimateSerializer extends AnimateSerializer_v898 {

    public static final NeteaseAnimateSerializer INSTANCE = new NeteaseAnimateSerializer();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, AnimatePacket packet) {
        super.serialize(buffer, helper, packet);
        // Java animations have no NetEase attack id, but the field is mandatory.
        VarInts.writeLong(buffer, 0);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, AnimatePacket packet) {
        super.deserialize(buffer, helper, packet);
        // ActorUniqueID is a signed zig-zag VarLong. Java attacks are handled through
        // interaction packets, so this value is not forwarded.
        VarInts.readLong(buffer);
    }

    private NeteaseAnimateSerializer() {
    }
}
