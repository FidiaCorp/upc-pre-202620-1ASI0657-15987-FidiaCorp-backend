package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.model.TwoFactorException;
import com.fidiacorp.credicasa.domain.ports.out.SecretProtectionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
@Component
public class AesSecretProtection implements SecretProtectionPort {
    private final byte[] key;
    public AesSecretProtection(@Value("${app.security.two-factor.encryption-key:}") String configuredKey) {
        byte[] decoded=null;
        try { decoded=Base64.getDecoder().decode(configuredKey); }
        catch (IllegalArgumentException ignored) { /* Fail closed when 2FA is used. */ }
        this.key=decoded!=null && decoded.length==32 ? decoded.clone() : null;
    }
    private Cipher cipher(int mode, UUID accountId, byte[] nonce) throws GeneralSecurityException {
        if(key==null) throw unavailable();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(accountId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    public String encrypt(UUID accountId,String secret) {
        try {
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,accountId,nonce).doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return "v1:"+Base64.getEncoder().encodeToString(ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array());
        } catch(GeneralSecurityException ex) { throw unavailable(); }
    }
    public String decrypt(UUID accountId,String encoded) {
        try {
            if(encoded==null || !encoded.startsWith("v1:")) throw unavailable();
            byte[] all=Base64.getDecoder().decode(encoded.substring(3));
            if(all.length<29) throw unavailable();
            byte[] nonce=java.util.Arrays.copyOf(all,12);
            return new String(cipher(Cipher.DECRYPT_MODE,accountId,nonce)
                .doFinal(all,12,all.length-12),StandardCharsets.UTF_8);
        } catch(GeneralSecurityException | IllegalArgumentException ex) { throw unavailable(); }
    }
    private static TwoFactorException unavailable() {
        return new TwoFactorException(503,"TWO_FACTOR_UNAVAILABLE","La verificación en dos pasos no está disponible. Contacte al administrador.");
    }
}
