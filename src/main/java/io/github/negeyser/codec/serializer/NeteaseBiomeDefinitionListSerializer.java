/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v800.serializer.BiomeDefinitionListSerializer_v800;
import org.cloudburstmc.protocol.bedrock.codec.v859.serializer.BiomeDefinitionListSerializer_v859;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionChunkGenData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.common.util.Preconditions;
import org.cloudburstmc.protocol.common.util.SequencedHashSet;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.cloudburstmc.protocol.common.util.index.IndexedList;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * NetEase biome definitions add a dimension id and a "vanilla" identifier after the rain flag.
 * These values are not used when translating Java biomes, so 0/empty is written for clients.
 */
public final class NeteaseBiomeDefinitionListSerializer {

    /**
     * NetEase 1.21.93 (protocol 819).
     */
    public static final class V819 extends BiomeDefinitionListSerializer_v800 {
        public static final V819 INSTANCE = new V819();

        @Override
        protected BiomeDefinitionData readDefinition(ByteBuf buffer, BedrockCodecHelper helper, List<String> strings) {
            Integer id = readDefinitionId(buffer, helper);
            float temperature = buffer.readFloatLE();
            float downfall = buffer.readFloatLE();
            float redSporeDensity = buffer.readFloatLE();
            float blueSporeDensity = buffer.readFloatLE();
            float ashDensity = buffer.readFloatLE();
            float whiteAshDensity = buffer.readFloatLE();
            float depth = buffer.readFloatLE();
            float scale = buffer.readFloatLE();
            Color mapWaterColor = new Color(buffer.readIntLE(), true);
            boolean rain = buffer.readBoolean();

            // NetEase only
            buffer.readIntLE();
            helper.readString(buffer);

            IndexedList<String> tags = helper.readOptional(buffer, null, byteBuf -> {
                int length = VarInts.readUnsignedInt(byteBuf);
                Preconditions.checkArgument(byteBuf.isReadable(length * 2), "Not enough readable bytes for tags");
                int[] indexes = new int[length];
                for (int i = 0; i < length; i++) {
                    indexes[i] = byteBuf.readUnsignedShortLE();
                }
                return new IndexedList<>(strings, indexes);
            });

            BiomeDefinitionChunkGenData chunkGenData = helper.readOptional(buffer, null,
                (buf, aHelper) -> readDefinitionChunkGen(buf, aHelper, strings));

            return new BiomeDefinitionData(id, temperature, downfall, redSporeDensity, blueSporeDensity,
                ashDensity, whiteAshDensity, depth, scale, mapWaterColor, rain, tags, chunkGenData);
        }

        @Override
        protected void writeDefinition(ByteBuf buffer, BedrockCodecHelper helper, BiomeDefinitionData definition,
                                       SequencedHashSet<String> strings) {
            writeDefinitionId(buffer, helper, definition);
            buffer.writeFloatLE(definition.getTemperature());
            buffer.writeFloatLE(definition.getDownfall());
            buffer.writeFloatLE(definition.getRedSporeDensity());
            buffer.writeFloatLE(definition.getBlueSporeDensity());
            buffer.writeFloatLE(definition.getAshDensity());
            buffer.writeFloatLE(definition.getWhiteAshDensity());
            buffer.writeFloatLE(definition.getDepth());
            buffer.writeFloatLE(definition.getScale());
            buffer.writeIntLE(definition.getMapWaterColor().getRGB());
            buffer.writeBoolean(definition.isRain());

            // NetEase only
            buffer.writeIntLE(0);
            helper.writeString(buffer, "");

            helper.writeOptionalNull(buffer, definition.getTags(), (byteBuf, aHelper, tags) -> {
                VarInts.writeUnsignedInt(byteBuf, tags.size());
                for (String tag : tags) {
                    byteBuf.writeShortLE(strings.addAndGetIndex(tag));
                }
            });
            helper.writeOptionalNull(buffer, definition.getChunkGenData(),
                (buf, aHelper, data) -> writeDefinitionChunkGen(buf, aHelper, data, strings));
        }

        private V819() {
        }
    }

    /**
     * NetEase 1.21.124 / 1.21.130 (protocols 860 and 898).
     */
    public static final class V860 extends BiomeDefinitionListSerializer_v859 {
        public static final V860 INSTANCE = new V860();

        @Override
        protected BiomeDefinitionData readDefinition(ByteBuf buffer, BedrockCodecHelper helper, List<String> strings) {
            Integer id = readDefinitionId(buffer, helper);
            float temperature = buffer.readFloatLE();
            float downfall = buffer.readFloatLE();
            float foliageSnow = buffer.readFloatLE();
            float depth = buffer.readFloatLE();
            float scale = buffer.readFloatLE();
            Color mapWaterColor = new Color(buffer.readIntLE(), true);
            boolean rain = buffer.readBoolean();

            // NetEase only
            buffer.readIntLE();
            helper.readString(buffer);

            IndexedList<String> tags = helper.readOptional(buffer, null, byteBuf -> {
                int length = VarInts.readUnsignedInt(byteBuf);
                Preconditions.checkArgument(byteBuf.isReadable(length * 2), "Not enough readable bytes for tags");
                int[] indexes = new int[length];
                for (int i = 0; i < length; i++) {
                    indexes[i] = byteBuf.readUnsignedShortLE();
                }
                return new IndexedList<>(strings, indexes);
            });

            BiomeDefinitionChunkGenData chunkGenData = helper.readOptional(buffer, null,
                (buf, aHelper) -> readDefinitionChunkGen(buf, aHelper, strings));

            return new BiomeDefinitionData(id, temperature, downfall, foliageSnow, depth, scale,
                mapWaterColor, rain, tags, chunkGenData);
        }

        @Override
        protected void writeDefinition(ByteBuf buffer, BedrockCodecHelper helper, BiomeDefinitionData definition,
                                       SequencedHashSet<String> strings) {
            writeDefinitionId(buffer, helper, definition);
            buffer.writeFloatLE(definition.getTemperature());
            buffer.writeFloatLE(definition.getDownfall());
            buffer.writeFloatLE(definition.getFoliageSnow());
            buffer.writeFloatLE(definition.getDepth());
            buffer.writeFloatLE(definition.getScale());
            buffer.writeIntLE(definition.getMapWaterColor().getRGB());
            buffer.writeBoolean(definition.isRain());

            // NetEase only
            buffer.writeIntLE(0);
            helper.writeString(buffer, "");

            helper.writeOptionalNull(buffer, definition.getTags(), (byteBuf, aHelper, tags) -> {
                VarInts.writeUnsignedInt(byteBuf, tags.size());
                for (String tag : tags) {
                    byteBuf.writeShortLE(strings.addAndGetIndex(tag));
                }
            });
            helper.writeOptionalNull(buffer, definition.getChunkGenData(),
                (buf, aHelper, data) -> writeDefinitionChunkGen(buf, aHelper, data, strings));
        }

        private V860() {
        }
    }

    private NeteaseBiomeDefinitionListSerializer() {
    }
}
