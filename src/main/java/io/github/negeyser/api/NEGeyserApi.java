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
 * Public API exposed by NEGeyserExtension to other Geyser extensions.
 *
 * <p>Usage from another extension:</p>
 * <pre>{@code
 * if (NEGeyserApi.isAvailable()) {
 *     NeteasePlayerInfo info = NEGeyserApi.get().getPlayerInfo(session);
 *     if (info != null) {
 *         // NetEase player
 *     }
 * }
 * }</pre>
 *
 * <p>The consuming extension must declare a dependency on {@code negeyser} in its
 * {@code extension.yml} so Geyser loads NEGeyserExtension first and shares these API classes.
 * Do not shade or bundle this package: the API instance is a static singleton owned by
 * NEGeyserExtension's class loader.</p>
 *
 * @since API 1
 */
public interface NEGeyserApi {

    /**
     * Binary API contract version. Incremented for incompatible API changes.
     */
    int API_VERSION = 1;

    /**
     * @return whether NEGeyserExtension is loaded and its API is installed
     */
    static boolean isAvailable() {
        return NEGeyserApiProvider.isAvailable();
    }

    /**
     * @return the installed API instance
     * @throws IllegalStateException if NEGeyserExtension is not loaded
     */
    static NEGeyserApi get() {
        return NEGeyserApiProvider.get();
    }

    /**
     * @return whether the session is a NetEase session that has completed NetEase authentication
     */
    boolean isNetease(GeyserSession session);

    /**
     * @return whether the connection is a NetEase session that has completed NetEase authentication
     */
    boolean isNetease(GeyserConnection connection);

    /**
     * @return the NetEase player info, or {@code null} for a non-NetEase session or before
     * NetEase authentication completes
     */
    NeteasePlayerInfo getPlayerInfo(GeyserSession session);

    /**
     * @return the NetEase player info, or {@code null} for a non-NetEase session or before
     * NetEase authentication completes
     */
    NeteasePlayerInfo getPlayerInfo(GeyserConnection connection);

    /**
     * Looks up a player by Geyser/Java UUID or by NetEase identity UUID.
     *
     * @return the NetEase player info, or {@code null} if no matching NetEase player is online
     */
    NeteasePlayerInfo getPlayerInfo(UUID uuid);

    /**
     * @return an immutable snapshot of all currently authenticated NetEase players
     */
    Collection<NeteasePlayerInfo> getOnlinePlayers();
}
