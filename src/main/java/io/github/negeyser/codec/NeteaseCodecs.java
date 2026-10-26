/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec;

import io.github.negeyser.codec.packet.ConfirmSkinPacket;
import io.github.negeyser.codec.packet.NeteaseJsonPacket;
import io.github.negeyser.codec.packet.PyRpcPacket;
import io.github.negeyser.codec.packet.SetDimensionLocalTimePacket;
import io.github.negeyser.codec.packet.StoreBuySuccessPacket;
import io.github.negeyser.codec.serializer.ConfirmSkinSerializer;
import io.github.negeyser.codec.serializer.NeteaseAnimateSerializer;
import io.github.negeyser.codec.serializer.NeteaseBiomeDefinitionListSerializer;
import io.github.negeyser.codec.serializer.NeteaseCommandRequestSerializer;
import io.github.negeyser.codec.serializer.NeteaseContainerOpenSerializer;
import io.github.negeyser.codec.serializer.NeteaseEnchantOptionsSerializer;
import io.github.negeyser.codec.serializer.NeteaseJsonSerializer;
import io.github.negeyser.codec.serializer.NeteasePlayerAuthInputSerializer;
import io.github.negeyser.codec.serializer.NeteaseResourcePackStackSerializer;
import io.github.negeyser.codec.serializer.NeteaseSetPlayerInventoryOptionsSerializer;
import io.github.negeyser.codec.serializer.NeteaseTextSerializer;
import io.github.negeyser.codec.serializer.PyRpcSerializer;
import io.github.negeyser.codec.serializer.SetDimensionLocalTimeSerializer;
import io.github.negeyser.codec.serializer.StoreBuySuccessSerializer;
import io.github.negeyser.protocol.NeteaseContainers;
import io.github.negeyser.protocol.NeteaseParticles;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.codec.v291.serializer.LevelEventSerializer_v291;
import org.cloudburstmc.protocol.bedrock.codec.v361.serializer.ClientCacheStatusSerializer_v361;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerInventoryOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NetEase Bedrock codecs.
 *
 * <p>NetEase clients speak RakNet protocol 8 and report the vanilla protocol numbers of the
 * Bedrock version they are based on (1.21.93, 1.21.124 and 1.21.130). The wire format is close
 * to vanilla but several packets have extra fields or shifted flag bits.</p>
 */
public final class NeteaseCodecs {

    private static final Map<Integer, BedrockCodec> CODECS = new ConcurrentHashMap<>();

    static {
        register(build(Bedrock_v819.CODEC, 819));
        register(build(Bedrock_v860.CODEC, 860));
        register(build(Bedrock_v898.CODEC, 898));
    }

    private static void register(BedrockCodec codec) {
        CODECS.put(codec.getProtocolVersion(), codec);
    }

    public static BedrockCodec get(int protocolVersion) {
        return CODECS.get(protocolVersion);
    }

    private static BedrockCodec build(BedrockCodec base, int protocolVersion) {
        // NetEase clients shift particle ids after SNEEZE by one (Nukkit-MOT Particle#getMultiversionId).
        // Build the shifted maps once and reuse them for every helper of this codec.
        NeteaseParticles.Maps particleMaps = NeteaseParticles.build(base);
        EntityDataTypeMap particleEntityData = particleMaps == null ? null
            : NeteaseParticles.shiftedEntityData(base.createHelper(), particleMaps.particles());

        BedrockCodec.Builder builder = base.toBuilder()
            .raknetProtocolVersion(8)
            .helper(() -> {
                BedrockCodecHelper helper = base.createHelper();
                helper.setEncodingSettings(EncodingSettings.CLIENT);
                NeteaseContainers.install(helper);
                NeteaseParticles.installEntityData(helper, particleEntityData);
                return helper;
            })
            .updateSerializer(PlayerAuthInputPacket.class, NeteasePlayerAuthInputSerializer.INSTANCE)
            .updateSerializer(TextPacket.class, protocolVersion >= 898 ? NeteaseTextSerializer.V898 : NeteaseTextSerializer.LEGACY)
            .updateSerializer(ClientCacheStatusPacket.class, ClientCacheStatusSerializer_v361.INSTANCE)
            .updateSerializer(PlayerEnchantOptionsPacket.class, NeteaseEnchantOptionsSerializer.INSTANCE);

        if (particleMaps != null) {
            builder.updateSerializer(LevelEventPacket.class,
                new LevelEventSerializer_v291(particleMaps.levelEvents()));
        }

        if (protocolVersion >= 860) {
            builder.updateSerializer(BiomeDefinitionListPacket.class, NeteaseBiomeDefinitionListSerializer.V860.INSTANCE);
            builder.updateSerializer(ContainerOpenPacket.class, NeteaseContainerOpenSerializer.INSTANCE);
            builder.updateSerializer(SetPlayerInventoryOptionsPacket.class, NeteaseSetPlayerInventoryOptionsSerializer.INSTANCE);
        } else {
            builder.updateSerializer(BiomeDefinitionListPacket.class, NeteaseBiomeDefinitionListSerializer.V819.INSTANCE);
        }

        if (protocolVersion >= 898) {
            builder.updateSerializer(AnimatePacket.class, NeteaseAnimateSerializer.INSTANCE);
            builder.updateSerializer(CommandRequestPacket.class, NeteaseCommandRequestSerializer.INSTANCE);
            builder.updateSerializer(ResourcePackStackPacket.class, NeteaseResourcePackStackSerializer.INSTANCE);
        }

        builder
            .registerPacket(PyRpcPacket::new, PyRpcSerializer.INSTANCE, PyRpcPacket.PACKET_ID, PacketRecipient.BOTH)
            .registerPacket(StoreBuySuccessPacket::new, StoreBuySuccessSerializer.INSTANCE,
                StoreBuySuccessPacket.PACKET_ID, PacketRecipient.BOTH)
            .registerPacket(NeteaseJsonPacket::new, NeteaseJsonSerializer.INSTANCE,
                NeteaseJsonPacket.PACKET_ID, PacketRecipient.BOTH)
            .registerPacket(SetDimensionLocalTimePacket::new, SetDimensionLocalTimeSerializer.INSTANCE,
                SetDimensionLocalTimePacket.PACKET_ID, PacketRecipient.CLIENT)
            .registerPacket(ConfirmSkinPacket::new, ConfirmSkinSerializer.INSTANCE,
                ConfirmSkinPacket.PACKET_ID, PacketRecipient.CLIENT);

        return builder.build();
    }

    private NeteaseCodecs() {
    }
}
