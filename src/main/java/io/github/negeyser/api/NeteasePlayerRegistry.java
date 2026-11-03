/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.api;

import io.github.negeyser.protocol.NeteaseProfile;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal live registry backing {@link NEGeyserApi}. Not intended to be called by consumers.
 *
 * @since API 1
 */
public final class NeteasePlayerRegistry {

    private static final Map<GeyserSession, NeteasePlayerInfo> PLAYERS = new ConcurrentHashMap<>();

    private NeteasePlayerRegistry() {
    }

    /**
     * Called after a NetEase login chain has been validated.
     */
    public static void register(GeyserSession session, NeteaseProfile profile,
                                boolean singleChain, int protocolVersion) {
        UUID uuid = session.javaUuid();
        if (uuid == null && session.getAuthData() != null) {
            uuid = session.getAuthData().uuid();
        }
        String name = session.getAuthData() != null
            ? session.getAuthData().name() : profile.displayName();
        PLAYERS.put(session, new Info(
            uuid,
            profile.identity(),
            name == null ? "" : name,
            profile.displayName() == null ? "" : profile.displayName(),
            profile.uid(),
            profile.xuid() == null ? "" : profile.xuid(),
            profile.environment() == null ? "" : profile.environment(),
            protocolVersion,
            singleChain,
            profile.issuedAt()
        ));
    }

    public static void unregister(GeyserSession session) {
        if (session != null) {
            PLAYERS.remove(session);
        }
    }

    public static NeteasePlayerInfo info(GeyserSession session) {
        return session == null ? null : PLAYERS.get(session);
    }

    public static NeteasePlayerInfo info(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        GeyserSession session = GeyserImpl.getInstance().connectionByUuid(uuid);
        if (session != null) {
            NeteasePlayerInfo info = PLAYERS.get(session);
            if (info != null) {
                return info;
            }
        }
        for (NeteasePlayerInfo info : PLAYERS.values()) {
            if (uuid.equals(info.uuid()) || uuid.equals(info.neteaseIdentity())) {
                return info;
            }
        }
        return null;
    }

    public static Collection<NeteasePlayerInfo> online() {
        return List.copyOf(PLAYERS.values());
    }

    private record Info(UUID uuid,
                        UUID neteaseIdentity,
                        String name,
                        String neteaseName,
                        long neteaseUid,
                        String xuid,
                        String environment,
                        int protocolVersion,
                        boolean singleChain,
                        long issuedAt) implements NeteasePlayerInfo {
    }
}
