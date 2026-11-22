package com.portability.domain.ports.out;

/** HMAC signing/verification of messages exchanged with the Central. */
public interface SignaturePort {

    String sign(String payload);

    boolean verify(String payload, String signature);
}
