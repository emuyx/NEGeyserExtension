/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import io.github.negeyser.NEGeyserExtension;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundGameRuleValuesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import java.util.Map;

/**
 * Bridges the Java {@code locator_bar} gamerule to the Bedrock/NetEase {@code locatorBar}
 * gamerule.
 *
 * <p>Upstream Geyser sends {@code locatorBar=true} unconditionally, relying on newer Bedrock
 * clients only rendering the bar when waypoints exist. NetEase clients show the bar by
 * default, so the extension hides it at login and requests the Java gamerule values once the
 * session has joined. The response is observed by a thin translator wrapper that delegates to
 * Geyser's original translator.</p>
 */
public final class NeteaseGameRules {

    private NeteaseGameRules() {
    }

    /**
     * Wraps Geyser's {@code ClientboundGameRuleValuesPacket} translator once so responses can
     * be inspected without changing the in-game gamerule form.
     */
    @SuppressWarnings("unchecked")
    public static void install() {
        Class<ClientboundGameRuleValuesPacket> packetClass = ClientboundGameRuleValuesPacket.class;
        PacketTranslator<? extends Packet> current =
            Registries.JAVA_PACKET_TRANSLATORS.get(packetClass);
        if (current instanceof LocatorBarTranslator) {
            return;
        }
        if (current == null) {
            NEGeyserExtension.LOG.warning(
                "Could not install the locator_bar gamerule bridge: translator not registered");
            return;
        }

        PacketTranslator<ClientboundGameRuleValuesPacket> original;
        try {
            original = (PacketTranslator<ClientboundGameRuleValuesPacket>) current;
        } catch (ClassCastException e) {
            NEGeyserExtension.LOG.warning(
                "Could not install the locator_bar gamerule bridge: unexpected translator type");
            return;
        }
        Registries.JAVA_PACKET_TRANSLATORS.register(packetClass, new LocatorBarTranslator(original));
    }

    /**
     * Called when a NetEase session has joined the Java server. Hides the locator bar until
     * the server's gamerule values arrive.
     */
    public static void requestOnJoin(GeyserSession session) {
        if (!(session.getUpstream() instanceof SpoofedUpstream upstream)) {
            return;
        }
        // Idempotent; also covers a translator registry that was not ready at startup.
        install();
        upstream.setLocatorBar(false);
        session.sendDownstreamGamePacket(
            new ServerboundClientCommandPacket(ClientCommand.REQUEST_GAMERULE_VALUES));
        if (NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.debugMode()) {
            NEGeyserExtension.LOG.info("[NETEASE-GAMERULE] requested locator_bar from the Java server");
        }
    }

    private static Boolean findLocatorBar(ClientboundGameRuleValuesPacket packet) {
        for (Map.Entry<?, String> entry : packet.getValues().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if ("minecraft:locator_bar".equals(key) || key.endsWith(":locator_bar")) {
                return Boolean.parseBoolean(entry.getValue());
            }
        }
        return null;
    }

    private static final class LocatorBarTranslator
        extends PacketTranslator<ClientboundGameRuleValuesPacket> {

        private final PacketTranslator<ClientboundGameRuleValuesPacket> delegate;

        private LocatorBarTranslator(PacketTranslator<ClientboundGameRuleValuesPacket> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void translate(GeyserSession session, ClientboundGameRuleValuesPacket packet) {
            if (session.getUpstream() instanceof SpoofedUpstream upstream) {
                Boolean value = findLocatorBar(packet);
                if (value != null) {
                    upstream.setLocatorBar(value);
                }
            }
            // Preserve Geyser's original behaviour (the gamerule edit form).
            delegate.translate(session, packet);
        }
    }
}
