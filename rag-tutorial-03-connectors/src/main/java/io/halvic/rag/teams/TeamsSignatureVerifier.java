package io.halvic.rag.teams;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Verifies Teams outgoing-webhook requests. Teams signs the raw request body
 * with HMAC-SHA256 using the webhook's security token (a Base64 key) and sends
 * the Base64 signature as {@code Authorization: HMAC <signature>}.
 */
@Component
@ConditionalOnProperty(prefix = "rag.teams", name = "hmac-secret")
public class TeamsSignatureVerifier {

    private static final String PREFIX = "HMAC ";

    private final byte[] key;

    public TeamsSignatureVerifier(TeamsProperties properties) {
        this.key = Base64.getDecoder().decode(properties.hmacSecret());
    }

    public boolean verify(String authorizationHeader, byte[] rawBody) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            return false;
        }
        String expected = computeSignature(rawBody);
        String provided = authorizationHeader.substring(PREFIX.length()).trim();
        // constant-time comparison to avoid timing attacks
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private String computeSignature(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
