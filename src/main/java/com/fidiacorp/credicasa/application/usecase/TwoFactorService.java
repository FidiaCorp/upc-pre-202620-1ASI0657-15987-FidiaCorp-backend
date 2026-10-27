package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.*;
import com.fidiacorp.credicasa.domain.service.TotpAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service
@Transactional(noRollbackFor=TwoFactorException.class)
public class TwoFactorService {
    public record Status(boolean enabled,boolean pending) {}
    public record Setup(String secret,String otpauthUri,int expiresIn) {}
    private final TwoFactorRepositoryPort states;
    private final AccountRepositoryPort accounts;
    private final PasswordHashingPort passwords;
    private final SecretProtectionPort secrets;
    private final TotpAlgorithm totp;
    private final Clock clock;
    public TwoFactorService(TwoFactorRepositoryPort states,AccountRepositoryPort accounts,
            PasswordHashingPort passwords,SecretProtectionPort secrets,TotpAlgorithm totp,Clock clock) {
        this.states=states;this.accounts=accounts;this.passwords=passwords;
        this.secrets=secrets;this.totp=totp;this.clock=clock;
    }
    public Status status(UUID id) {
        TwoFactorState s=states.lockForAccount(id);
        return new Status(s.enabled,!s.enabled && s.encryptedSecret!=null
            && s.pendingExpiresAt!=null && clock.instant().isBefore(s.pendingExpiresAt));
    }
    public Setup setup(UUID id,String password) {
        TwoFactorState s=states.lockForAccount(id);checkLock(s);verifyPassword(s,password);
        if(s.enabled) throw new TwoFactorException(409,"TWO_FACTOR_ALREADY_ENABLED","La verificación en dos pasos ya está activa.");
        String secret=totp.newSecret();String encrypted=secrets.encrypt(id,secret);
        s.encryptedSecret=encrypted;s.pendingExpiresAt=clock.instant().plusSeconds(600);s.lastAcceptedStep=-1;
        states.save(s);
        String email=accounts.findById(id).orElseThrow().email();
        String uri="otpauth://totp/"+URLEncoder.encode("CrediCasa:"+email,StandardCharsets.UTF_8).replace("+","%20")
            +"?secret="+secret+"&issuer=CrediCasa&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret,uri,600);
    }
    public Status confirm(UUID id,String otp) {
        TwoFactorState s=states.lockForAccount(id);checkLock(s);
        if(s.enabled) throw new TwoFactorException(409,"TWO_FACTOR_ALREADY_ENABLED","La verificación en dos pasos ya está activa.");
        if(s.encryptedSecret==null || s.pendingExpiresAt==null || !clock.instant().isBefore(s.pendingExpiresAt))
            throw new TwoFactorException(400,"TWO_FACTOR_SETUP_EXPIRED","La configuración expiró. Genere un nuevo secreto.");
        consume(s,otp);s.enabled=true;s.pendingExpiresAt=null;success(s);states.save(s);
        return new Status(true,false);
    }
    public Status disable(UUID id,String password,String otp) {
        TwoFactorState s=states.lockForAccount(id);checkLock(s);verifyPassword(s,password);
        if(!s.enabled) throw new TwoFactorException(409,"TWO_FACTOR_NOT_ENABLED","La verificación en dos pasos no está activa.");
        consume(s,otp);s.enabled=false;s.encryptedSecret=null;s.pendingExpiresAt=null;
        success(s);states.save(s);return new Status(false,false);
    }
    /** Called only after the primary password has been authenticated, before minting any JWT. */
    public void verifyLogin(UUID id,String otp) {
        TwoFactorState s=states.lockForAccount(id);checkLock(s);
        if(!s.enabled) return;
        if(otp==null || otp.isBlank())
            throw new TwoFactorException(401,"TWO_FACTOR_REQUIRED","Introduzca el código de su aplicación autenticadora.");
        consume(s,otp);success(s);states.save(s);
    }
    private void verifyPassword(TwoFactorState s,String password) {
        Account account=accounts.findById(s.accountId).orElseThrow();
        if(password==null || !passwords.matches(password,account.passwordHash()))
            fail(s,"INVALID_CREDENTIALS","Credenciales inválidas.");
    }
    private void consume(TwoFactorState s,String otp) {
        long step=clock.instant().getEpochSecond()/30;
        String expected=totp.code(secrets.decrypt(s.accountId,s.encryptedSecret),step);
        if(otp==null || !otp.matches("[0-9]{6}") || step<=s.lastAcceptedStep
            || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),otp.getBytes(StandardCharsets.US_ASCII)))
            fail(s,"TWO_FACTOR_INVALID","El código es inválido, ya fue utilizado o expiró.");
        s.lastAcceptedStep=step;
    }
    private void checkLock(TwoFactorState s) {
        if(s.lockedUntil!=null && clock.instant().isBefore(s.lockedUntil)) throw locked(s);
        if(s.lockedUntil!=null) {s.lockedUntil=null;s.failedAttempts=0;states.save(s);}
    }
    private void fail(TwoFactorState s,String code,String message) {
        s.failedAttempts++;
        if(s.failedAttempts>=5) s.lockedUntil=clock.instant().plusSeconds(900);
        states.save(s);
        if(s.lockedUntil!=null) throw locked(s);
        throw new TwoFactorException(401,code,message);
    }
    private TwoFactorException locked(TwoFactorState s) {
        long seconds=Math.max(1,Duration.between(clock.instant(),s.lockedUntil).toSeconds());
        return new TwoFactorException(429,"TWO_FACTOR_LOCKED","Demasiados intentos. Intente de nuevo más tarde.",seconds);
    }
    private void success(TwoFactorState s) {s.failedAttempts=0;s.lockedUntil=null;}
}
