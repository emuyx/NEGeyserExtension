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
 * Generic NetEase entity event channel (packet id 203).
 */
public final class NeteaseJsonPacket implements BedrockPacket {

    public static final int PACKET_ID = 203;

    public enum Event {
        SET_ENTITY_GRAVITY("SET_ENTITY_GRAVITY", "gravity"),
        SET_JUMP_POWER("SET_JUMP_POWER", "value"),
        SET_MAX_AUTO_STEP("SET_MAX_AUTO_STEP", "value");

        private final String eventName;
        private final String valueName;

        Event(String eventName, String valueName) {
            this.eventName = eventName;
            this.valueName = valueName;
        }

        public String eventName() {
            return eventName;
        }

        public String valueName() {
            return valueName;
        }
    }

    private long entityId;
    private double value;
    private Event event = Event.SET_ENTITY_GRAVITY;

    public NeteaseJsonPacket() {
    }

    public NeteaseJsonPacket(long entityId, Event event, double value) {
        this.entityId = entityId;
        this.event = event;
        this.value = value;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
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
    public NeteaseJsonPacket clone() {
        try {
            return (NeteaseJsonPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
