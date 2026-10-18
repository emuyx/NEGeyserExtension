/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.network;

import io.github.negeyser.config.NeteaseConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.bootstrap.ServerBootstrapConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.network.BedrockPingHandler;
import org.geysermc.geyser.network.RaknetServer;
import org.geysermc.geyser.network.bedrock.raknet.RakConnectionRequestHandler;
import org.geysermc.geyser.network.bedrock.raknet.RakPingHandler;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Rebinds Geyser's RakNet listener with a child handler that understands NetEase connections.
 *
 * <p>Geyser's own {@link ServerBootstrap} cannot be reused because Netty forbids replacing the
 * event loop group of an already configured bootstrap. A new bootstrap is created instead, with
 * every option, attribute and handler copied from Geyser's original bootstrap, so cookies, MTU,
 * rate limiting, HAProxy handling and reuse-port keep their configured values.</p>
 */
public final class ListenerRestarter {

    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;

    private ListenerRestarter() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void restart(NeteaseConfig neteaseConfig) throws Exception {
        GeyserImpl geyser = GeyserImpl.getInstance();
        RaknetServer server = (RaknetServer) field(GeyserImpl.class, "geyserServer").get(geyser);
        if (server == null) {
            geyser.getLogger().warning("NetEase extension: RakNet is disabled (NetherNet-only transport), "
                + "so NetEase clients cannot be accepted. Enable the RakNet transport in Geyser's config.");
            return;
        }

        ServerBootstrap original = (ServerBootstrap) field(RaknetServer.class, "bootstrap").get(server);
        ChannelFuture[] oldFutures = (ChannelFuture[]) field(RaknetServer.class, "bootstrapFutures").get(server);
        int listenCount = oldFutures == null ? 1 : Math.max(1, oldFutures.length);

        GeyserConfig config = geyser.config();
        InetSocketAddress address = new InetSocketAddress(config.bedrock().address(), config.bedrock().raknetPort());

        // Everything that can fail (group creation, option copying) happens before the original
        // listener is touched, so a failure leaves regular Bedrock players connected.
        EventLoopGroup group = TRANSPORT.eventLoopGroupFactory().apply(listenCount,
            new DefaultThreadFactory("GeyserServer", true));
        EventLoopGroup childGroup = TRANSPORT.eventLoopGroupFactory().apply(
            Math.max(1, Runtime.getRuntime().availableProcessors() * 2),
            new DefaultThreadFactory("GeyserServerChild", true));

        boolean rakCookiesEnabled = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));
        NeteaseServerInitializer initializer = new NeteaseServerInitializer(geyser, neteaseConfig, rakCookiesEnabled);

        ServerBootstrap bootstrap = new ServerBootstrap()
            .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
            .group(group, childGroup)
            .childHandler(initializer);

        ServerBootstrapConfig originalConfig = original.config();
        for (Map.Entry<ChannelOption<?>, Object> entry : originalConfig.options().entrySet()) {
            bootstrap.option((ChannelOption) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<ChannelOption<?>, Object> entry : originalConfig.childOptions().entrySet()) {
            bootstrap.childOption((ChannelOption) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<AttributeKey<?>, Object> entry : originalConfig.attrs().entrySet()) {
            bootstrap.attr((AttributeKey) entry.getKey(), entry.getValue());
        }
        for (Map.Entry<AttributeKey<?>, Object> entry : originalConfig.childAttrs().entrySet()) {
            bootstrap.childAttr((AttributeKey) entry.getKey(), entry.getValue());
        }
        if (originalConfig.handler() != null) {
            bootstrap.handler(originalConfig.handler());
        }

        // Close the original listener and terminate its event loops.
        try {
            server.shutdown();
        } catch (Throwable e) {
            // A previous failed restart can leave one of the groups null. Keep going: the
            // replacement listener below is still installed and usable.
            geyser.getLogger().warning("NetEase extension: issue while closing the original listener: " + e);
        }

        // Make the RaknetServer state valid immediately: Geyser's shutdown path dereferences
        // these fields, so they must never be left null even if binding fails below.
        setField(server, "group", group);
        setField(server, "childGroup", childGroup);
        setField(server, "playerGroup", initializer.getEventLoopGroup());
        setField(server, "bootstrapFutures", new ChannelFuture[0]);

        BedrockPingHandler pingHandler = new BedrockPingHandler(geyser);
        ChannelFuture[] futures = new ChannelFuture[listenCount];
        try {
            for (int i = 0; i < listenCount; i++) {
                ChannelFuture future = bootstrap.bind(address);
                future.addListener((ChannelFutureListener) bound -> {
                    if (!bound.isSuccess()) {
                        geyser.getLogger().error(
                            "NetEase extension: unable to rebind the Bedrock listener", bound.cause());
                        return;
                    }
                    Channel channel = bound.channel();
                    channel.pipeline()
                        .addBefore(RakServerOfflineHandler.NAME, RakConnectionRequestHandler.NAME,
                            new RakConnectionRequestHandler(server))
                        .addAfter(RakServerOfflineHandler.NAME, RakPingHandler.NAME,
                            new RakPingHandler(pingHandler));
                });
                futures[i] = future;
            }

            setField(server, "bootstrapFutures", futures);
            for (ChannelFuture future : futures) {
                future.sync();
                if (!future.isSuccess()) {
                    throw new IllegalStateException("Unable to bind the NetEase Bedrock listener", future.cause());
                }
            }
        } catch (Throwable e) {
            // Leave the new groups in RaknetServer so Geyser can shut them down cleanly.
            group.shutdownGracefully(100, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            childGroup.shutdownGracefully(100, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            throw e;
        }

        geyser.getLogger().info("NetEase extension: Bedrock listener restarted; RakNet protocol 8 (NetEase) "
            + "and regular Bedrock clients share port " + address.getPort() + ".");
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = field(target.getClass(), name);
        field.set(target, value);
    }
}
