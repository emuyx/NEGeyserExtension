/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import java.util.Map;
import java.util.UUID;

/**
 * Identity information extracted from a validated NetEase login chain.
 */
public record NeteaseProfile(String displayName,
                             UUID identity,
                             String xuid,
                             long uid,
                             String environment,
                             String identityPublicKey,
                             long issuedAt,
                             Map<String, Object> claims) {
}
