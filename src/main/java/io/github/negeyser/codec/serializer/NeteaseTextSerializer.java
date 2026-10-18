/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec.serializer;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketSerializer;
import org.cloudburstmc.protocol.bedrock.codec.v685.serializer.TextSerializer_v685;
import org.cloudburstmc.protocol.bedrock.codec.v898.serializer.TextSerializer_v898;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.common.util.TextConverter;

/**
 * NetEase text packet layouts.
 *
 * <p>NetEase 1.21.93/1.21.124 clients disconnect when they receive a normal CHAT packet, so chat
 * is downgraded to SYSTEM. 1.21.130 added an AuthorAndMessage category and two extra fields
 * (parameters and, for chat, a filtered-message suffix) around the vanilla layout.</p>
 */
public final class NeteaseTextSerializer {

    public static final BedrockPacketSerializer<TextPacket> LEGACY = new TextSerializer_v685() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            TextPacket.Type type = packet.getType();
            if (type == TextPacket.Type.CHAT) {
                type = TextPacket.Type.SYSTEM;
            }

            buffer.writeByte(type.ordinal());
            buffer.writeBoolean(packet.isNeedsTranslation());

            switch (type) {
                case WHISPER:
                case ANNOUNCEMENT:
                    helper.writeString(buffer, packet.getSourceName());
                case CHAT:
                case RAW:
                case TIP:
                case SYSTEM:
                case JSON:
                case WHISPER_JSON:
                case ANNOUNCEMENT_JSON:
                    helper.writeString(buffer, packet.getMessage());
                    break;
                case TRANSLATION:
                case POPUP:
                case JUKEBOX_POPUP:
                    helper.writeString(buffer, packet.getMessage());
                    helper.writeArray(buffer, packet.getParameters(), helper::writeString);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported TextType " + type);
            }

            helper.writeString(buffer, packet.getXuid());
            helper.writeString(buffer, packet.getPlatformChatId());
            helper.writeString(buffer, packet.getFilteredMessage());
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            TextPacket.Type type = TextPacket.Type.values()[buffer.readUnsignedByte()];
            packet.setType(type);
            packet.setNeedsTranslation(buffer.readBoolean());

            switch (type) {
                case CHAT:
                case WHISPER:
                case ANNOUNCEMENT:
                    packet.setSourceName(helper.readString(buffer));
                case RAW:
                case TIP:
                case SYSTEM:
                case JSON:
                case WHISPER_JSON:
                case ANNOUNCEMENT_JSON:
                    packet.setMessage(helper.readString(buffer));
                    break;
                case TRANSLATION:
                case POPUP:
                case JUKEBOX_POPUP:
                    packet.setMessage(helper.readString(buffer));
                    helper.readArray(buffer, packet.getParameters(), helper::readString);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported TextType " + type);
            }

            packet.setXuid(helper.readString(buffer));
            packet.setPlatformChatId(helper.readString(buffer));
            packet.setFilteredMessage(helper.readString(buffer));
        }
    };

    public static final BedrockPacketSerializer<TextPacket> V898 = new TextSerializer_v898() {
        @Override
        public void serialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            TextPacket.Type type = packet.getType();
            if (type != TextPacket.Type.CHAT && type != TextPacket.Type.WHISPER && type != TextPacket.Type.ANNOUNCEMENT) {
                super.serialize(buffer, helper, packet);
                return;
            }

            TextConverter converter = helper.getTextConverter();
            CharSequence message = packet.getMessage(CharSequence.class);
            Boolean needsTranslation = converter.needsTranslation(message);
            buffer.writeBoolean(needsTranslation != null ? needsTranslation : packet.isNeedsTranslation());

            // AuthorAndMessage category
            buffer.writeByte(1);
            helper.writeString(buffer, "chat");
            helper.writeString(buffer, "whisper");
            helper.writeString(buffer, "announcement");
            buffer.writeByte(type.ordinal());
            helper.writeString(buffer, packet.getSourceName());

            String text = converter.serialize(message);
            helper.writeString(buffer, text.isEmpty() ? " " : text);

            // NetEase inserts the parameters list before the standard packet suffix.
            helper.writeArray(buffer, packet.getParameters(), helper::writeString);
            helper.writeString(buffer, packet.getXuid());
            helper.writeString(buffer, packet.getPlatformChatId());

            String filtered = converter.serialize(packet.getFilteredMessage(CharSequence.class));
            helper.writeOptional(buffer, s -> !s.isEmpty(), filtered,
                (buf, codecHelper, s) -> codecHelper.writeString(buf, s));
        }

        @Override
        public void deserialize(ByteBuf buffer, BedrockCodecHelper helper, TextPacket packet) {
            // The category byte follows needsTranslation. Anything other than AuthorAndMessage
            // uses the vanilla 1.21.130 layout.
            if (buffer.getUnsignedByte(buffer.readerIndex() + 1) != 1) {
                super.deserialize(buffer, helper, packet);
                return;
            }

            TextConverter converter = helper.getTextConverter();
            boolean needsTranslation = buffer.readBoolean();
            packet.setNeedsTranslation(needsTranslation);
            buffer.readUnsignedByte(); // AuthorAndMessage
            for (int i = 0; i < 3; i++) {
                helper.readString(buffer); // category names
            }
            packet.setType(TextPacket.Type.values()[buffer.readUnsignedByte()]);
            packet.setSourceName(helper.readString(buffer));
            packet.setMessage(converter.deserialize(helper.readString(buffer), needsTranslation));
            helper.readArray(buffer, packet.getParameters(), helper::readString);
            packet.setXuid(helper.readString(buffer));
            packet.setPlatformChatId(helper.readString(buffer));
            String filtered = helper.readOptional(buffer, "", (buf, codecHelper) -> codecHelper.readString(buf));
            packet.setFilteredMessage(converter.deserialize(filtered, needsTranslation));
        }
    };

    private NeteaseTextSerializer() {
    }
}
