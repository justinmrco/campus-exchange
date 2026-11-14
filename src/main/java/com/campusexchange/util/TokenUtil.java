package com.campusexchange.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * TokenUtil generates cryptographically secure random tokens
 * used for physical handover verification between buyer and seller.
 */
@Component
public class TokenUtil {

    private static final int TOKEN_BYTES = 24;

    public String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        // URL-safe base64, no padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Derives an 8-character uppercase hex display code from the first 4 bytes of a token.
     * This is the code shown on-screen at meetup — the other party must read and submit it.
     * Example: "A3F9B2C1"
     */
    public String toDisplayCode(String token) {
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02X", decoded[i] & 0xFF));
        }
        return sb.toString();
    }
}
