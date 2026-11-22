package com.portability.adapters.out.central;

import com.portability.domain.ports.out.SignaturePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HMAC-SHA256 signature of messages exchanged with the Central. */
@Component
public class HmacSignatureService implements SignaturePort {

    private static final String ALGORITHM = "HmacSHA256";

    private final String hmacSecret;

    public HmacSignatureService(@Value("${central.hmac-secret}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    @Override
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign Central message", exception);
        }
    }

    @Override
    public boolean verify(String payload, String signature) {
        if (signature == null) {
            return false;
        }
        try {
            byte[] received = Base64.getDecoder().decode(signature);
            byte[] expected = Base64.getDecoder().decode(sign(payload));
            return MessageDigest.isEqual(expected, received);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
