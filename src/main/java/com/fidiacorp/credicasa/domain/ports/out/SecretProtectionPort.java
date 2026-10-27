package com.fidiacorp.credicasa.domain.ports.out;
import java.util.UUID;
public interface SecretProtectionPort {
    String encrypt(UUID accountId, String secret);
    String decrypt(UUID accountId, String encryptedSecret);
}
