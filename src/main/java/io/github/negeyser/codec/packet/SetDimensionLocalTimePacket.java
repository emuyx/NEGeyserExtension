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
 * NetEase-only dimension local time override (packet id 208).
 */
public final class SetDimensionLocalTimePacket implements BedrockPacket {

    public static final int PACKET_ID = 208;

    private boolean localTimeEnabled;
    private int time;
    private boolean tickDayTime = true;

    public boolean isLocalTimeEnabled() {
        return localTimeEnabled;
    }

    public void setLocalTimeEnabled(boolean localTimeEnabled) {
        this.localTimeEnabled = localTimeEnabled;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public boolean isTickDayTime() {
        return tickDayTime;
    }

    public void setTickDayTime(boolean tickDayTime) {
        this.tickDayTime = tickDayTime;
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
    public SetDimensionLocalTimePacket clone() {
        try {
            return (SetDimensionLocalTimePacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
