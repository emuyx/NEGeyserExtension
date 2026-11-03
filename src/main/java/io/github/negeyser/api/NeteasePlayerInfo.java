/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.api;

import java.util.UUID;

/**
 * Immutable snapshot of a NetEase player's identity and connection information.
 *
 * <p>The snapshot is created when the NetEase login chain has been validated and is not updated
 * afterwards. The same object is returned for the lifetime of the session.</p>
 *
 * @since API 1
 */
public interface NeteasePlayerInfo {

    /**
     * The player UUID used by Geyser/the Java server. This is normally the Floodgate/linked UUID,
     * or the NetEase identity UUID when no separate UUID was assigned. May be {@code null} for
     * the very short window before the player entity is created.
     */
    UUID uuid();

    /**
     * The identity UUID taken from the validated NetEase certificate chain.
     */
    UUID neteaseIdentity();

    /**
     * The name Geyser/the Java server uses for this player. If
     * {@code translate-netease-names} is enabled this is the transliterated name.
     */
    String name();

    /**
     * The original NetEase display name before any optional transliteration.
     */
    String neteaseName();

    /**
     * The numeric NetEase account UID.
     */
    long neteaseUid();

    /**
     * The NetEase XUID from the validated login chain. May be empty.
     */
    String xuid();

    /**
     * The NetEase environment string from the login chain (for example {@code obfuscated}).
     */
    String environment();

    /**
     * The Bedrock protocol version reported by the NetEase client (819, 860 or 898).
     */
    int protocolVersion();

    /**
     * Whether this session authenticated with a single-entry, relayed certificate chain.
     */
    boolean singleChain();

    /**
     * The {@code issuedAt} timestamp from the validated NetEase login chain.
     */
    long issuedAt();
}
