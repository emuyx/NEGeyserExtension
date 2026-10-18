/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Self-contained Bedrock login cryptography.
 *
 * <p>Cloudburst's {@code EncryptionUtils} performs Microsoft discovery requests in its static
 * initializer. NetEase login validation is local, so relying on that class would make NetEase
 * logins fail on servers that cannot reach Microsoft's discovery endpoints.</p>
 */
public final class NeteaseCrypto {

    public static final String ALGORITHM_TYPE = AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final KeyPairGenerator KEY_PAIR_GENERATOR;

    static {
        // Java 8u231+ needs secp384r1 explicitly enabled for some providers.
        String namedGroups = System.getProperty("jdk.tls.namedGroups");
        System.setProperty("jdk.tls.namedGroups",
            namedGroups == null || namedGroups.isEmpty() ? "secp384r1" : namedGroups + ",secp384r1");
        try {
            KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance("EC");
            KEY_PAIR_GENERATOR.initialize(new ECGenParameterSpec("secp384r1"));
        } catch (Exception e) {
            throw new AssertionError("Unable to initialize EC key generation", e);
        }
    }

    private NeteaseCrypto() {
    }

    public static ECPublicKey parseKey(String base64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey) KeyFactory.getInstance("EC")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    public static KeyPair createKeyPair() {
        return KEY_PAIR_GENERATOR.generateKeyPair();
    }

    public static byte[] generateToken() {
        byte[] token = new byte[16];
        RANDOM.nextBytes(token);
        return token;
    }

    public static byte[] verifyClientData(String clientJwt, PublicKey identityPublicKey) throws JoseException {
        if (clientJwt == null || clientJwt.isEmpty()) {
            return null;
        }
        JsonWebSignature signature = new JsonWebSignature();
        signature.setCompactSerialization(clientJwt);
        signature.setKey(identityPublicKey);
        return signature.verifySignature() ? signature.getUnverifiedPayloadBytes() : null;
    }

    public static String createHandshakeJwt(KeyPair serverKeyPair, byte[] token) throws JoseException {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(ALGORITHM_TYPE);
        signature.setHeader("x5u", Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
        signature.setKey(serverKeyPair.getPrivate());

        JwtClaims claims = new JwtClaims();
        claims.setClaim("salt", Base64.getEncoder().encodeToString(token));
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }

    public static SecretKey getSecretKey(KeyPair serverKeyPair, PublicKey clientPublicKey, byte[] token)
            throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeyPair.getPrivate());
        agreement.doPhase(clientPublicKey, true);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(token);
        digest.update(agreement.generateSecret());
        return new SecretKeySpec(digest.digest(), "AES");
    }
}
