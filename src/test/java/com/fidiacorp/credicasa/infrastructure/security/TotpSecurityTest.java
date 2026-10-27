package com.fidiacorp.credicasa.infrastructure.security;
import com.fidiacorp.credicasa.domain.service.TotpAlgorithm;
import com.fidiacorp.credicasa.domain.model.TwoFactorException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class TotpSecurityTest {
    @Test void matchesAllRfc6238Sha1VectorsIncluding64BitCounter() {
        TotpAlgorithm totp=new TotpAlgorithm();
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        long[] times={59L,1111111109L,1111111111L,1234567890L,2000000000L,20000000000L};
        String[] expected={"94287082","07081804","14050471","89005924","69279037","65353130"};
        for(int i=0;i<times.length;i++) assertThat(totp.code(secret,times[i]/30,8)).isEqualTo(expected[i]);
        assertThat(totp.code(secret,59/30)).isEqualTo("287082");
    }
    @Test void generatesDistinctTwentyByteBase32Secrets() {
        TotpAlgorithm totp=new TotpAlgorithm();
        String first=totp.newSecret(),second=totp.newSecret();
        assertThat(first).matches("[A-Z2-7]{32}").isNotEqualTo(second);
        assertThat(second).matches("[A-Z2-7]{32}");
    }
    @Test void encryptsWithUniqueNonceAndBindsCiphertextToAccount() {
        AesSecretProtection protection=new AesSecretProtection("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        UUID id=UUID.randomUUID();String secret=new TotpAlgorithm().newSecret();
        String encrypted=protection.encrypt(id,secret);
        assertThat(encrypted).startsWith("v1:").doesNotContain(secret)
            .isNotEqualTo(protection.encrypt(id,secret));
        assertThat(protection.decrypt(id,encrypted)).isEqualTo(secret);
        assertThatThrownBy(() -> protection.decrypt(UUID.randomUUID(),encrypted)).isInstanceOf(TwoFactorException.class);
        assertThatThrownBy(() -> protection.decrypt(id,"v1:broken")).isInstanceOf(TwoFactorException.class);
        String tampered=encrypted.substring(0,encrypted.length()-4)+"AAAA";
        assertThatThrownBy(() -> protection.decrypt(id,tampered)).isInstanceOf(TwoFactorException.class);
    }
    @Test void missingInvalidOrWrongEncryptionKeyFailsClosed() {
        UUID id=UUID.randomUUID();
        for(String key:new String[]{"","broken","c2hvcnQ="}) {
            assertThatThrownBy(() -> new AesSecretProtection(key).encrypt(id,"secret"))
                .isInstanceOfSatisfying(TwoFactorException.class,e -> assertThat(e.status()).isEqualTo(503));
        }
        var first=new AesSecretProtection("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String encrypted=first.encrypt(id,"secret");
        assertThatThrownBy(() -> new AesSecretProtection("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=").decrypt(id,encrypted))
            .isInstanceOf(TwoFactorException.class);
    }
}
