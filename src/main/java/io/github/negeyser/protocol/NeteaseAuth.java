/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Validates the three-JWT login chain sent by NetEase clients.
 *
 * <p>Layout:</p>
 * <ol>
 *     <li>self-signed client certificate,</li>
 *     <li>a certificate signed directly by the NetEase root key,</li>
 *     <li>the identity certificate signed by the key advertised in the second certificate.</li>
 * </ol>
 *
 * <p>The final payload contains the same {@code extraData} object that vanilla Bedrock puts in
 * its identity claims.</p>
 *
 * <p>NetEase room (cross-server) relays may forward only the final, self-signed JWT. When
 * explicitly allowed, that single-entry chain is verified against its own {@code x5u} key and
 * the NetEase root key check is skipped.</p>
 */
public final class NeteaseAuth {

    /**
     * NetEase's Bedrock certificate authority (ECDSA P-384).
     */
    private static final String NETEASE_ROOT_KEY_BASE64 =
        "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEEsmU+IF/XeAF3yiqJ7Ko36btx6JtdB26wV9Eyw4AYR/nmesznkfXxwQ4B0NkSnGIZccbb2f3nFUYughKSoAcNHx+lQm8F9h9RwhrNgeN907z06LUA2AqWcwqasxyaU0E";

    private static final AlgorithmConstraints ALGORITHM_CONSTRAINTS =
        new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, NeteaseCrypto.ALGORITHM_TYPE);

    private static final ECPublicKey NETEASE_ROOT_KEY;

    static {
        try {
            NETEASE_ROOT_KEY = NeteaseCrypto.parseKey(NETEASE_ROOT_KEY_BASE64);
        } catch (Exception e) {
            throw new AssertionError("Unable to initialize the NetEase root key", e);
        }
    }

    private NeteaseAuth() {
    }

    /**
     * Validates a NetEase certificate chain.
     *
     * @param payload the chain sent in the login packet
     * @param requireObtEnvironment whether the profile environment must be "obt"
     * @param allowSingleChain whether a single-entry, self-signed chain may be accepted
     * @return the NetEase profile
     * @throws Exception if the chain is malformed, not signed by NetEase, or missing identity data
     */
    public static NeteaseProfile validate(CertificateChainPayload payload, boolean requireObtEnvironment,
                                          boolean allowSingleChain) throws Exception {
        List<String> chain = payload.getChain();
        int chainSize = chain == null ? 0 : chain.size();
        if (chainSize == 1 && !allowSingleChain) {
            throw new IllegalStateException("NetEase login sent a single-entry certificate chain; "
                + "enable allow-single-chain to accept it");
        }
        if (chainSize != 3 && !(chainSize == 1 && allowSingleChain)) {
            throw new IllegalStateException("Expected a three element NetEase certificate chain, got "
                + (chain == null ? "null" : chain.size()));
        }
        boolean singleChain = chainSize == 1;

        ECPublicKey currentKey = null;
        Map<String, Object> lastPayload = null;

        for (int i = 0; i < chain.size(); i++) {
            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(chain.get(i));
            signature.setAlgorithmConstraints(ALGORITHM_CONSTRAINTS);

            String x5u = signature.getHeader(HeaderParameterNames.X509_URL);
            if (x5u == null) {
                throw new IllegalStateException("NetEase certificate " + i + " has no x5u header");
            }
            ECPublicKey advertisedKey = NeteaseCrypto.parseKey(x5u);

            if (currentKey == null) {
                currentKey = advertisedKey;
            } else if (!currentKey.equals(advertisedKey)) {
                throw new IllegalStateException("NetEase certificate chain is broken at index " + i);
            }

            signature.setKey(currentKey);
            if (!signature.verifySignature()) {
                throw new IllegalStateException("NetEase certificate " + i + " failed signature validation");
            }

            if (i == 1 && !currentKey.equals(NETEASE_ROOT_KEY)) {
                throw new IllegalStateException("Certificate chain was not signed by NetEase");
            }

            lastPayload = JsonUtil.parseJson(signature.getUnverifiedPayload());
            Object identityPublicKey = lastPayload.get("identityPublicKey");
            if (!(identityPublicKey instanceof String key)) {
                throw new IllegalStateException("NetEase certificate " + i + " has no identityPublicKey");
            }
            currentKey = NeteaseCrypto.parseKey(key);
        }

        Map<?, ?> extraData = asMap(lastPayload.get("extraData"));
        if (extraData == null) {
            throw new IllegalStateException("NetEase identity certificate has no extraData");
        }

        String displayName = asString(extraData.get("displayName"));
        String identityString = asString(extraData.get("identity"));
        String xuid = firstString(extraData, "XUID", "xuid");
        String environment = firstString(extraData, "env", "environment");
        if (environment == null) {
            environment = asString(lastPayload.get("env"));
        }

        if (displayName == null || displayName.isBlank()) {
            throw new IllegalStateException("NetEase identity has no display name");
        }
        if (requireObtEnvironment && !"obt".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("NetEase login environment is not obt: " + environment);
        }

        UUID identity;
        long uid;
        if (singleChain) {
            // Room logins identify players through the NetEase fields; their XUID is often
            // empty. Prefer netease_uid, then a numeric XUID, then a stable hash.
            String neteaseUuidString = firstString(extraData, "netease_uuid", "neteaseUuid");
            long neteaseUid = parseUid(extraData.get("netease_uid"));
            if (neteaseUid == 0L
                && (neteaseUuidString == null || neteaseUuidString.isBlank())) {
                throw new IllegalStateException(
                    "Single-chain NetEase login must contain netease_uid or netease_uuid");
            }

            uid = neteaseUid;
            if (uid == 0L) {
                uid = parseUid(extraData.get("uid"));
            }
            if (uid == 0L && xuid != null) {
                try {
                    uid = Long.parseLong(xuid);
                } catch (NumberFormatException ignored) {
                    // Fall through to a deterministic hash.
                }
            }
            if (uid == 0L) {
                String hashSource = neteaseUuidString;
                if (hashSource == null || hashSource.isBlank()) {
                    hashSource = identityString;
                }
                if (hashSource == null || hashSource.isBlank()) {
                    hashSource = displayName;
                }
                uid = stableLong(hashSource);
            }
            if (uid < 0L) {
                uid = uid == Long.MIN_VALUE ? 1L : -uid;
            }

            identity = parseUuid(neteaseUuidString);
            if (identity == null) {
                identity = parseUuid(identityString);
            }
        } else {
            // Original three-element chain behavior, kept byte-for-byte so enabling
            // allow-single-chain cannot change existing NetEase players' identities.
            identity = parseUuid(identityString);
            uid = parseUid(extraData.get("uid"));
            if (uid == 0L && xuid != null) {
                try {
                    uid = Long.parseLong(xuid);
                } catch (NumberFormatException ignored) {
                    // Fall through to a deterministic hash.
                }
            }
            if (uid == 0L) {
                uid = stableLong(identity != null ? identity.toString() : displayName);
            }
            if (uid < 0L) {
                uid = uid == Long.MIN_VALUE ? 1L : -uid;
            }
        }
        // Floodgate derives the Java UUID from the xuid, so always use a stable numeric value.
        xuid = Long.toString(uid);

        if (identity == null) {
            identity = UUID.nameUUIDFromBytes(("netease:" + uid).getBytes(StandardCharsets.UTF_8));
        }

        Object rawIssuedAt = lastPayload.get("iat");
        long issuedAt = rawIssuedAt instanceof Number number ? number.longValue() : -1L;

        String identityPublicKey = asString(lastPayload.get("identityPublicKey"));
        if (identityPublicKey == null) {
            throw new IllegalStateException("NetEase identity certificate has no public key");
        }

        return new NeteaseProfile(displayName, identity, xuid, uid,
            environment == null ? "" : environment.toLowerCase(Locale.ROOT), identityPublicKey, issuedAt, lastPayload);
    }

    /**
     * Verifies the client data JWT against the identity public key from the chain.
     */
    public static byte[] verifyClientData(String clientJwt, String identityPublicKey) throws Exception {
        if (clientJwt == null || clientJwt.isEmpty() || identityPublicKey == null) {
            return null;
        }
        return NeteaseCrypto.verifyClientData(clientJwt, NeteaseCrypto.parseKey(identityPublicKey));
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String asString(Object value) {
        return value instanceof String string ? string : value == null ? null : String.valueOf(value);
    }

    private static String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String value = asString(map.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String hex = value.replace("-", "");
        if (hex.length() == 32) {
            try {
                return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID, fall back to hashing below.
            }
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static long parseUid(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static long stableLong(String value) {
        byte[] hash;
        try {
            hash = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        long result = 0L;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (hash[i] & 0xFFL);
        }
        if (result == 0L || result == Long.MIN_VALUE) {
            return 1L;
        }
        return Math.abs(result);
    }

}
