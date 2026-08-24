package io.halvic.rag.slack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Verifies Slack request signatures (the "v0" scheme): Slack signs
 * {@code v0:<timestamp>:<raw body>} with HMAC-SHA256 using the app's signing
 * secret and sends the hex digest as {@code X-Slack-Signature: v0=<hex>}.
 * Requests older than ~5 minutes are rejected to prevent replay attacks.
 */
@Component
@ConditionalOnProperty(prefix = "rag.slack", name = "signing-secret")
public class SlackSignatureVerifier {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final byte[] signingSecret;

    public SlackSignatureVerifier(SlackProperties properties) {
        this.signingSecret = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(String timestampHeader, String signatureHeader, String rawBody) {
        if (timestampHeader == null || signatureHeader == null) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return false;
        }
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), Instant.now()).abs();
        if (age.compareTo(TOLERANCE) > 0) {
            return false;
        }
        String expected = "v0=" + hexHmac("v0:" + timestampHeader + ":" + rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hexHmac(String baseString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
