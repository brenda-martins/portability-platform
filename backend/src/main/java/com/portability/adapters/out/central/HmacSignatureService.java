package com.portability.adapters.out.central;

import com.portability.domain.ports.out.SignaturePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HMAC-SHA256 signature of messages exchanged with the Central. */
@Component
public class HmacSignatureService implements SignaturePort {

    @Value("${central.hmac-secret}")
    private String hmacSecret;

    @Override
    public String sign(String payload) {
        // TODO: HmacSHA256 with the configured secret, Base64-encoded.
        throw new UnsupportedOperationException("TODO: sign payload");
    }

    @Override
    public boolean verify(String payload, String signature) {
        // TODO: constant-time comparison against the computed signature.
        throw new UnsupportedOperationException("TODO: verify signature");
    }
}
