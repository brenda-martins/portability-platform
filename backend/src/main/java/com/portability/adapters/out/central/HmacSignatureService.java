package com.portability.adapters.out.central;

import com.portability.adapters.in.config.CentralProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HMAC-SHA256 signature of messages exchanged with the Central. */
@Component
public class HmacSignatureService {

    private static final String ALGORITHM = "HmacSHA256";

    private final String hmacSecret;

    @Autowired
    public HmacSignatureService(CentralProperties properties) {
        this(properties.hmacSecret());
    }

    public HmacSignatureService(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign Central message", exception);
        }
    }

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
