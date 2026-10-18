/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.network;

import io.github.negeyser.config.NeteaseConfig;
import io.github.negeyser.handler.NeteaseUpstreamHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.network.bedrock.raknet.RakServerInitializer;
import org.geysermc.geyser.network.bedrock.GeyserBedrockPeer;
import org.geysermc.geyser.network.bedrock.InvalidPacketHandler;
import org.geysermc.geyser.network.bedrock.UpstreamPacketHandler;
import org.geysermc.geyser.session.GeyserSession;

/**
 * Same as Geyser's RakNet initializer, except that connections arriving over RakNet protocol 8
 * (NetEase) get NetEase framing, compression and packet handling. Every other RakNet version
 * uses Geyser's stock pipeline unchanged, so international Bedrock clients are unaffected.
 */
public final class NeteaseServerInitializer extends RakServerInitializer {

    /** NetEase clients use RakNet protocol 8 regardless of the Bedrock version they emulate. */
    public static final int NETEASE_RAKNET_VERSION = 8;

    private final NeteaseConfig config;

    public NeteaseServerInitializer(GeyserImpl geyser, NeteaseConfig config, boolean rakCookiesEnabled) {
        super(geyser, rakCookiesEnabled);
        this.config = config;
    }

    @Override
    protected void preInitChannel(Channel channel) throws Exception {
        super.preInitChannel(channel);

        if (raknetVersion(channel) == NETEASE_RAKNET_VERSION) {
            // NetEase starts uncompressed; compression is negotiated in NetworkSettings.
            channel.pipeline().replace(CompressionCodec.NAME, CompressionCodec.NAME,
                new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false));
        }
    }

    @Override
    protected void initPacketCodec(Channel channel) throws Exception {
        if (raknetVersion(channel) == NETEASE_RAKNET_VERSION) {
            // NetEase uses the v3 batch framing (VarInt packet ids and lengths) even on RakNet 8.
            channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
            return;
        }
        super.initPacketCodec(channel);
    }

    @Override
    public void initSession(BedrockServerSession bedrockServerSession) {
        try {
            bedrockServerSession.setLogging(geyser.config().debugMode());
            boolean netease = raknetVersion(bedrockServerSession.getPeer().getChannel()) == NETEASE_RAKNET_VERSION;

            if (!netease && config.onlyNeteaseClients()) {
                bedrockServerSession.disconnect(config.disconnectMessage());
                return;
            }

            GeyserSession session = new GeyserSession(geyser, bedrockServerSession, getEventLoopGroup().next());

            if (!bedrockServerSession.isSubClient()) {
                Channel channel = bedrockServerSession.getPeer().getChannel();
                try {
                    channel.pipeline().addAfter(BedrockPeer.NAME, InvalidPacketHandler.NAME,
                        new InvalidPacketHandler(session));
                } catch (Exception ignored) {
                    // Handler already present.
                }
            }

            if (netease) {
                bedrockServerSession.setPacketHandler(new NeteaseUpstreamHandler(geyser, session));
                if (config.debugMode()) {
                    installOutboundTrace(bedrockServerSession.getPeer().getChannel());
                }
            } else {
                bedrockServerSession.setPacketHandler(new UpstreamPacketHandler(geyser, session));
            }
        } catch (Throwable e) {
            geyser.getLogger().error("Error occurred while initializing player!", e);
            bedrockServerSession.disconnect(e.getMessage());
        }
    }

    /**
     * Debug helper: logs every outbound stage so a silently-dropped message can be traced to the
     * exact codec. Enable {@code debug-mode} in config.yml to use it.
     */
    private static void installOutboundTrace(Channel channel) {
        channel.pipeline().addLast("netease-trace-peer", tracer("peer"));
        channel.pipeline().addBefore(BedrockPacketCodec.NAME, "netease-trace-packet", tracer("after-packet-codec"));
        channel.pipeline().addBefore(BedrockBatchEncoder.NAME, "netease-trace-batch", tracer("after-batch-encoder"));
        channel.pipeline().addBefore(CompressionCodec.NAME, "netease-trace-compression", tracer("after-compression"));
        channel.pipeline().addBefore(FrameIdCodec.NAME, "netease-trace-frame", tracer("after-frame-id"));
    }

    private static ChannelOutboundHandlerAdapter tracer(String stage) {
        return new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                String type;
                if (msg instanceof BedrockPacketWrapper wrapper && wrapper.getPacket() != null) {
                    type = wrapper.getPacket().getClass().getSimpleName();
                } else {
                    type = msg.getClass().getSimpleName();
                }
                io.github.negeyser.NEGeyserExtension.LOG.info("[NETEASE-TRACE] " + stage + " " + type);
                try {
                    ctx.write(msg, promise);
                } catch (Throwable t) {
                    io.github.negeyser.NEGeyserExtension.LOG.error(
                        "[NETEASE-TRACE] " + stage + " failed for " + type + " pipeline=" + ctx.pipeline().names(), t);
                    throw t;
                }
            }
        };
    }

    @Override
    protected BedrockPeer createPeer(Channel channel) {
        return new GeyserBedrockPeer(channel, this::createSession);
    }

    private static int raknetVersion(Channel channel) {
        Integer version = channel.config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
        return version == null ? -1 : version;
    }
}
