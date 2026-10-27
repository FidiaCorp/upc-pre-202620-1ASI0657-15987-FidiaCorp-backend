package com.fidiacorp.credicasa.domain.service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;
/** RFC 6238 / HMAC-SHA1, six digits, 30 seconds, no expired-step grace period. */
public class TotpAlgorithm {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public String newSecret() {
        byte[] bytes = new byte[20]; new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder();
        int buffer=0, bits=0;
        for (byte value : bytes) {
            buffer=(buffer << 8) | (value & 255); bits+=8;
            while (bits>=5) { bits-=5; result.append(ALPHABET.charAt((buffer >>> bits)&31)); }
        }
        if(bits>0) result.append(ALPHABET.charAt((buffer << (5-bits))&31));
        return result.toString();
    }
    public String code(String base32Secret, long step) { return code(base32Secret, step, 6); }
    public String code(String base32Secret, long step, int digits) {
        if (digits != 6 && digits != 8) throw new IllegalArgumentException("Unsupported OTP length");
        try {
            Mac mac=Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(base32Secret), "HmacSHA1"));
            byte[] digest=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset=digest[digest.length-1]&15;
            int number=((digest[offset]&127)<<24)|((digest[offset+1]&255)<<16)
                |((digest[offset+2]&255)<<8)|(digest[offset+3]&255);
            int modulus=digits==6 ? 1000000 : 100000000;
            return String.format(Locale.ROOT, "%0"+digits+"d", number%modulus);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("TOTP no disponible", ex); }
    }
    private byte[] decode(String secret) {
        java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();
        int buffer=0,bits=0;
        for(char letter:secret.toCharArray()) {
            int value=ALPHABET.indexOf(letter);
            if(value<0) throw new IllegalArgumentException("Invalid secret encoding");
            buffer=(buffer<<5)|value;bits+=5;
            if(bits>=8) {bits-=8;result.write((buffer >>> bits)&255);}
        }
        return result.toByteArray();
    }
}
