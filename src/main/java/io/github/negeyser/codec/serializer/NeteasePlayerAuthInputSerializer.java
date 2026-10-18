/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 *
 * Based on the NetEase player auth input layout used by Geyser's NetEase branch.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v662.serializer.PlayerAuthInputSerializer_v662;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.math.BigInteger;
import java.util.Set;

/**
 * PlayerAuthInput for NetEase 1.21.93 and newer.
 *
 * <p>NetEase reserves two wire flag bits after STOP_FLYING and appends a few extra fields at the
 * end of the packet. The extra values have no Java/Geyser equivalent, so they are read and
 * discarded.</p>
 */
public final class NeteasePlayerAuthInputSerializer extends PlayerAuthInputSerializer_v662 {

    public static final NeteasePlayerAuthInputSerializer INSTANCE = new NeteasePlayerAuthInputSerializer();

    private static final int NETEASE_INPUT_BITS = computeInputBits();
    private static final PlayerAuthInputData[] WIRE_TO_INPUT = createWireToInputMapping();

    @Override
    public void serialize(ByteBuf buffer, BedrockCodecHelper helper, PlayerAuthInputPacket packet) {
        Vector3f rotation = packet.getRotation();
        buffer.writeFloatLE(rotation.getX());
        buffer.writeFloatLE(rotation.getY());
        helper.writeVector3f(buffer, packet.getPosition());
        buffer.writeFloatLE(packet.getMotion().getX());
        buffer.writeFloatLE(packet.getMotion().getY());
        buffer.writeFloatLE(rotation.getZ());

        BigInteger flags = BigInteger.ZERO;
        for (PlayerAuthInputData input : packet.getInputData()) {
            int wireBit = toWireBit(input.ordinal());
            if (wireBit >= 0) {
                flags = flags.setBit(wireBit);
            }
        }
        VarInts.writeUnsignedBigVarInt(buffer, flags);

        VarInts.writeUnsignedInt(buffer, packet.getInputMode().ordinal());
        VarInts.writeUnsignedInt(buffer, packet.getPlayMode().ordinal());
        writeInteractionModel(buffer, helper, packet);
        helper.writeVector2f(buffer, packet.getInteractRotation());
        VarInts.writeUnsignedLong(buffer, packet.getTick());
        helper.writeVector3f(buffer, packet.getDelta());

        // NetEase: camera departed
        buffer.writeBoolean(false);

        if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
            this.writeItemUseTransaction(buffer, helper, packet.getItemUseTransaction());
        }
        if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
            helper.writeItemStackRequest(buffer, packet.getItemStackRequest());
        }
        if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            VarInts.writeInt(buffer, packet.getPlayerActions().size());
            for (PlayerBlockActionData action : packet.getPlayerActions()) {
                writePlayerBlockActionData(buffer, helper, action);
            }
        }
        if (packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            helper.writeVector2f(buffer, packet.getVehicleRotation());
            VarInts.writeLong(buffer, packet.getPredictedVehicle());
        }
        helper.writeVector2f(buffer, packet.getAnalogMoveVector());
        helper.writeVector3f(buffer, packet.getCameraOrientation());
        helper.writeVector2f(buffer, packet.getRawMoveVector());

        // NetEase: third person perspective, camera rotation, dirty flag, ground state, reset position
        buffer.writeBoolean(false);
        buffer.writeFloatLE(0f);
        buffer.writeFloatLE(0f);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeByte(0);
    }

    @Override
    public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, PlayerAuthInputPacket packet) {
        float x = buffer.readFloatLE();
        float y = buffer.readFloatLE();
        packet.setPosition(helper.readVector3f(buffer));
        packet.setMotion(Vector2f.from(buffer.readFloatLE(), buffer.readFloatLE()));
        float z = buffer.readFloatLE();
        packet.setRotation(Vector3f.from(x, y, z));

        BigInteger flags = VarInts.readUnsignedBigVarInt(buffer, NETEASE_INPUT_BITS);
        Set<PlayerAuthInputData> inputData = packet.getInputData();
        inputData.clear();
        int bitsToCheck = Math.min(flags.bitLength(), WIRE_TO_INPUT.length);
        for (int wireBit = 0; wireBit < bitsToCheck; wireBit++) {
            PlayerAuthInputData input = WIRE_TO_INPUT[wireBit];
            if (input != null && flags.testBit(wireBit)) {
                inputData.add(input);
            }
        }

        packet.setInputMode(INPUT_MODES[VarInts.readUnsignedInt(buffer)]);
        packet.setPlayMode(CLIENT_PLAY_MODES[VarInts.readUnsignedInt(buffer)]);
        readInteractionModel(buffer, helper, packet);
        packet.setInteractRotation(helper.readVector2f(buffer));
        packet.setTick(VarInts.readUnsignedLong(buffer));
        packet.setDelta(helper.readVector3f(buffer));

        // NetEase: camera departed
        buffer.readBoolean();

        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)) {
            packet.setItemUseTransaction(this.readItemUseTransaction(buffer, helper));
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_ITEM_STACK_REQUEST)) {
            packet.setItemStackRequest(helper.readItemStackRequest(buffer));
        }
        if (inputData.contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            helper.readArray(buffer, packet.getPlayerActions(), VarInts::readInt, this::readPlayerBlockActionData, 32);
        }
        if (inputData.contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE)) {
            packet.setVehicleRotation(helper.readVector2f(buffer));
            packet.setPredictedVehicle(VarInts.readLong(buffer));
        }
        packet.setAnalogMoveVector(helper.readVector2f(buffer));
        packet.setCameraOrientation(helper.readVector3f(buffer));
        packet.setRawMoveVector(helper.readVector2f(buffer));

        // NetEase extras: third person perspective, camera rotation, dirty flag, ground state, reset position
        buffer.readBoolean();
        buffer.readFloatLE();
        buffer.readFloatLE();
        buffer.readBoolean();
        buffer.readBoolean();
        buffer.readByte();
    }

    private static int toWireBit(int ordinal) {
        // Bits 44 and 45 are reserved by NetEase; all later vanilla flags are shifted by two.
        int wireBit = rawWireBit(ordinal);
        return wireBit < NETEASE_INPUT_BITS ? wireBit : -1;
    }

    private static int rawWireBit(int ordinal) {
        int stopFlying = PlayerAuthInputData.STOP_FLYING.ordinal();
        return ordinal <= stopFlying ? ordinal : ordinal + 2;
    }

    private static int computeInputBits() {
        int max = 0;
        for (PlayerAuthInputData input : PlayerAuthInputData.values()) {
            max = Math.max(max, rawWireBit(input.ordinal()));
        }
        return max + 1;
    }

    private static PlayerAuthInputData[] createWireToInputMapping() {
        PlayerAuthInputData[] mapping = new PlayerAuthInputData[NETEASE_INPUT_BITS];
        for (PlayerAuthInputData input : PlayerAuthInputData.values()) {
            int wireBit = rawWireBit(input.ordinal());
            if (wireBit < mapping.length) {
                mapping[wireBit] = input;
            }
        }
        return mapping;
    }

    private NeteasePlayerAuthInputSerializer() {
    }
}
