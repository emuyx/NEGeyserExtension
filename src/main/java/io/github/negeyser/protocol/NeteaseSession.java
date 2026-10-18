/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.geysermc.geyser.session.GeyserSession;

/**
 * Per-session state for a NetEase connection.
 */
public final class NeteaseSession {

    private final GeyserSession session;
    private final int protocolVersion;
    private final BedrockCodec codec;
    private boolean loginDeferred;
    private boolean authenticated;
    private long uid;

    public NeteaseSession(GeyserSession session, int protocolVersion, BedrockCodec codec) {
        this.session = session;
        this.protocolVersion = protocolVersion;
        this.codec = codec;
        this.uid = Math.abs(System.nanoTime());
    }

    public GeyserSession session() {
        return session;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public BedrockCodec codec() {
        return codec;
    }

    public boolean loginDeferred() {
        return loginDeferred;
    }

    public void setLoginDeferred(boolean loginDeferred) {
        this.loginDeferred = loginDeferred;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public long uid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }
}
