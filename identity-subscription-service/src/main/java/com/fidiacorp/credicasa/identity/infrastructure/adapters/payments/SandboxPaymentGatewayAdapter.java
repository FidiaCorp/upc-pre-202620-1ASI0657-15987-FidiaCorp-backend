package com.fidiacorp.credicasa.identity.infrastructure.adapters.payments;

import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeRequest;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeResult;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeStatus;
import com.fidiacorp.credicasa.identity.domain.model.PaymentGateway;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionPlan;
import com.fidiacorp.credicasa.identity.domain.ports.out.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adaptador sandbox verificable para Culqi, Izipay y Stripe (US05). No mueve dinero real.
 * <ul>
 *   <li>Tokens que contienen {@code DECLINED_INSUFFICIENT_FUNDS} → REJECTED / INSUFFICIENT_FUNDS.</li>
 *   <li>Tokens que contienen {@code DECLINED} → REJECTED / CARD_DECLINED.</li>
 *   <li>Cualquier otro token válido → APPROVED con referencia {@code GATEWAY-nonce-firma} (HMAC-SHA256 sobre
 *       gateway|nonce|cuenta|plan|importe|moneda, truncado a 160 bits), que {@link #isAuthentic} verifica en tiempo constante.</li>
 * </ul>
 */
@Component
public class SandboxPaymentGatewayAdapter implements PaymentGatewayPort {
    private static final String HMAC = "HmacSHA256";
    private static final Pattern REFERENCE = Pattern.compile("^(CULQI|IZIPAY|STRIPE)-([0-9a-f]{32})-([0-9a-f]{40})$");
    private static final List<PaymentGateway> GATEWAYS = List.of(
            new PaymentGateway("CULQI", "Culqi (sandbox)", List.of("PEN", "USD"), new BigDecimal("3.44"), new BigDecimal("0.00"), true),
            new PaymentGateway("IZIPAY", "Izipay (sandbox)", List.of("PEN"), new BigDecimal("3.99"), new BigDecimal("0.00"), true),
            new PaymentGateway("STRIPE", "Stripe (test mode)", List.of("PEN", "USD"), new BigDecimal("3.60"), new BigDecimal("1.00"), true));

    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public SandboxPaymentGatewayAdapter(@Value("${app.payments.sandbox-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.payments.sandbox-secret debe tener al menos 32 bytes.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public List<PaymentGateway> availableGateways() { return GATEWAYS; }

    @Override
    public Optional<PaymentGateway> findGateway(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return GATEWAYS.stream().filter(g -> g.code().equals(normalized)).findFirst();
    }

    @Override
    public GatewayChargeResult charge(GatewayChargeRequest request) {
        PaymentGateway gateway = findGateway(request.gatewayCode())
                .orElseThrow(() -> new IllegalArgumentException("Pasarela no soportada: " + request.gatewayCode()));
        BigDecimal commission = request.amount().multiply(gateway.commissionPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN).add(gateway.fixedFee());
        String token = request.cardToken() == null ? "" : request.cardToken().toUpperCase(Locale.ROOT);
        if (token.contains("DECLINED_INSUFFICIENT_FUNDS")) {
            return rejected(gateway, request, commission, "INSUFFICIENT_FUNDS", "Fondos insuficientes. Intenta con otra tarjeta o pasarela.");
        }
        if (token.contains("DECLINED")) {
            return rejected(gateway, request, commission, "CARD_DECLINED", "La entidad emisora rechazó la tarjeta. Intenta con otra pasarela.");
        }
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        String signature = sign(gateway.code(), nonce, request.accountId(), request.plan(), request.amount(), request.currency());
        String reference = gateway.code() + "-" + nonce + "-" + signature;
        return new GatewayChargeResult(gateway.code(), GatewayChargeStatus.APPROVED, reference,
                "AUTH-" + nonce.substring(0, 8).toUpperCase(Locale.ROOT), null, request.amount(), commission, request.currency(),
                "Cobro aprobado en modo sandbox por " + gateway.displayName() + ".");
    }

    @Override
    public boolean isAuthentic(String transactionReference, String gatewayCode, UUID accountId, SubscriptionPlan plan,
                               BigDecimal amount, String currency) {
        if (transactionReference == null || gatewayCode == null) return false;
        var matcher = REFERENCE.matcher(transactionReference);
        if (!matcher.matches() || !matcher.group(1).equals(gatewayCode.trim().toUpperCase(Locale.ROOT))) return false;
        String expected = sign(matcher.group(1), matcher.group(2), accountId, plan, amount, currency);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), matcher.group(3).getBytes(StandardCharsets.US_ASCII));
    }

    private GatewayChargeResult rejected(PaymentGateway gateway, GatewayChargeRequest request, BigDecimal commission,
                                         String declineCode, String message) {
        return new GatewayChargeResult(gateway.code(), GatewayChargeStatus.REJECTED, null, null, declineCode, request.amount(),
                commission, request.currency(), message);
    }

    private String sign(String gateway, String nonce, UUID accountId, SubscriptionPlan plan, BigDecimal amount, String currency) {
        String payload = gateway + "|" + nonce + "|" + accountId + "|" + plan.name() + "|"
                + amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "|" + currency;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            // HMAC-SHA256 truncado a 160 bits (RFC 2104 §5): la referencia cabe en VARCHAR(100).
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).substring(0, 40);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la referencia sandbox", e);
        }
    }
}
