/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.packet;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketType;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NetEase-only packet that confirms which skins were accepted by the server's skin checks.
 */
public final class ConfirmSkinPacket implements BedrockPacket {

    public static final int PACKET_ID = 228;

    private List<Entry> entries = new ArrayList<>();

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    @Override
    public PacketSignal handle(BedrockPacketHandler handler) {
        return PacketSignal.UNHANDLED;
    }

    @Override
    public BedrockPacketType getPacketType() {
        // Cloudburst has no dedicated constant for NetEase-only packets. The enum name is only
        // used for diagnostics; encoding uses the id registered with the codec.
        return BedrockPacketType.UNKNOWN;
    }

    @Override
    public ConfirmSkinPacket clone() {
        try {
            return (ConfirmSkinPacket) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public static final class Entry {
        private boolean valid = true;
        private UUID uuid;
        private byte[] skinData = new byte[0];
        private String uid = "";
        private String geometry = "";

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public UUID getUuid() {
            return uuid;
        }

        public void setUuid(UUID uuid) {
            this.uuid = uuid;
        }

        public byte[] getSkinData() {
            return skinData;
        }

        public void setSkinData(byte[] skinData) {
            this.skinData = skinData == null ? new byte[0] : skinData;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid == null ? "" : uid;
        }

        public String getGeometry() {
            return geometry;
        }

        public void setGeometry(String geometry) {
            this.geometry = geometry == null ? "" : geometry;
        }
    }
}
