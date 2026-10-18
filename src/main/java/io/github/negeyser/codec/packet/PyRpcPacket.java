/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.packet;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;
import org.cloudburstmc.protocol.common.PacketSignal;

/**
 * NetEase Python scripting RPC packet (packet id 200). Payloads are ignored.
 */
public final class PyRpcPacket implements BedrockPacket {

    public static final int PACKET_ID = 200;

    private byte[] data = new byte[0];
    private long msgId;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data == null ? new byte[0] : data;
    }

    public long getMsgId() {
        return msgId;
    }

    public void setMsgId(long msgId) {
        this.msgId = msgId;
    }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        return BedrockPacketType.UNKNOWN;
    }

    @Override
    public PyRpcPacket clone() {
        try {
            return (PyRpcPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
