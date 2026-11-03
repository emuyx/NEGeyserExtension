/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.api;

import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Collection;
import java.util.UUID;

/**
 * Internal holder for the singleton {@link NEGeyserApi} instance.
 *
 * <p>Consumers should use {@link NEGeyserApi#get()} and {@link NEGeyserApi#isAvailable()}.
 * This class is public only so NEGeyserExtension can install the implementation; it is not
 * part of the stable consumer API.</p>
 *
 * @since API 1
 */
public final class NEGeyserApiProvider {

    private static volatile NEGeyserApi instance;

    private NEGeyserApiProvider() {
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static NEGeyserApi get() {
        NEGeyserApi api = instance;
        if (api == null) {
            throw new IllegalStateException("NEGeyserExtension is not installed");
        }
        return api;
    }

    /**
     * Installs the default implementation. Safe to call repeatedly (startup and reload).
     */
    public static synchronized void installDefault() {
        if (instance == null) {
            instance = new NeteaseApiImpl();
        }
    }

    private static final class NeteaseApiImpl implements NEGeyserApi {

        @Override
        public boolean isNetease(GeyserSession session) {
            return NeteasePlayerRegistry.info(session) != null;
        }

        @Override
        public boolean isNetease(GeyserConnection connection) {
            return getPlayerInfo(connection) != null;
        }

        @Override
        public NeteasePlayerInfo getPlayerInfo(GeyserSession session) {
            return NeteasePlayerRegistry.info(session);
        }

        @Override
        public NeteasePlayerInfo getPlayerInfo(GeyserConnection connection) {
            if (connection instanceof GeyserSession session) {
                return NeteasePlayerRegistry.info(session);
            }
            return connection == null ? null : NeteasePlayerRegistry.info(connection.playerUuid());
        }

        @Override
        public NeteasePlayerInfo getPlayerInfo(UUID uuid) {
            return NeteasePlayerRegistry.info(uuid);
        }

        @Override
        public Collection<NeteasePlayerInfo> getOnlinePlayers() {
            return NeteasePlayerRegistry.online();
        }
    }
}
