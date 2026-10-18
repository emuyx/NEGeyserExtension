/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser;

import io.github.negeyser.config.NeteaseConfig;
import io.github.negeyser.config.NeteaseConfigLoader;
import io.github.negeyser.network.ListenerRestarter;
import io.github.negeyser.protocol.NeteaseGameRules;
import io.github.negeyser.protocol.NeteaseRegistries;
import io.github.negeyser.protocol.NeteaseResourcePacks;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.session.GeyserSession;

/**
 * Geyser extension entry point.
 */
public final class NEGeyserExtension implements Extension {

    public static ExtensionLogger LOG;
    public static NeteaseConfig CONFIG;

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        start("startup");
    }

    @Subscribe
    public void onPostReload(GeyserPostReloadEvent event) {
        // /geyser reload keeps the old listener alive; rebind it with the current configuration.
        start("reload");
    }

    @Subscribe
    public void onSessionJoin(SessionJoinEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            NeteaseGameRules.requestOnJoin(session);
        }
    }

    @Subscribe
    public void onSessionLoadResourcePacks(SessionLoadResourcePacksEvent event) {
        NeteaseResourcePacks.apply(event);
    }

    private void start(String reason) {
        LOG = logger();
        CONFIG = NeteaseConfigLoader.load(this);

        LOG.info("NEGeyserExtension is starting (" + reason + ").");

        if ("reload".equals(reason)) {
            NeteaseRegistries.invalidate();
        }

        try {
            NeteaseGameRules.install();
        } catch (Throwable e) {
            LOG.warning("Could not install the locator_bar gamerule bridge: " + e.getMessage());
        }

        try {
            NeteaseResourcePacks.reload(dataFolder());
        } catch (Throwable e) {
            LOG.warning("Could not load the routed resource packs: " + e.getMessage());
        }

        // Register the NetEase protocol versions before any session can log in. This mutates
        // Geyser's shared versioned registries, so do it synchronously rather than racing with
        // sessions that are iterating them.
        try {
            NeteaseRegistries.ensureInitialized();
        } catch (Throwable e) {
            LOG.error("Could not register the NetEase protocol registries. NetEase clients "
                + "cannot connect until this is fixed.", e);
        }

        // Preload fallback item names off the session thread. Doing this lazily during packet
        // translation can stall the login long enough for the client to time out.
        io.github.negeyser.protocol.NeteaseFallbacks.preloadNames("en_us", "zh_cn");

        try {
            ListenerRestarter.restart(CONFIG);
        } catch (Throwable e) {
            LOG.error("Could not install the NetEase-aware Bedrock listener. "
                + "Regular Bedrock clients keep working, but NetEase clients cannot connect.", e);
        }
    }
}
