package com.fidiacorp.credicasa.identity.infrastructure.adapters.payments;

import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeRequest;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeResult;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeStatus;
import com.fidiacorp.credicasa.identity.domain.model.PaymentGateway;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US05: pasarelas sandbox con aprobación/rechazo determinista y referencias HMAC verificables. */
class SandboxPaymentGatewayAdapterTest {
    private static final String SECRET = "unit-test-sandbox-secret-with-at-least-32-bytes";
    private final SandboxPaymentGatewayAdapter adapter = new SandboxPaymentGatewayAdapter(SECRET);
    private final UUID account = UUID.randomUUID();

    private GatewayChargeResult charge(String gateway, SubscriptionPlan plan, String token) {
        return adapter.charge(new GatewayChargeRequest(gateway, account, plan, plan.monthlyFee(), "PEN", token));
    }

    @Test
    @DisplayName("Catálogo: CULQI, IZIPAY y STRIPE en sandbox con monedas y comisiones")
    void listsGateways() {
        assertThat(adapter.availableGateways()).extracting(PaymentGateway::code).containsExactly("CULQI", "IZIPAY", "STRIPE");
        assertThat(adapter.availableGateways()).allMatch(PaymentGateway::sandbox);
        assertThat(adapter.findGateway(" izipay ")).get().matches(g -> g.supportsCurrency("PEN") && !g.supportsCurrency("USD"));
        assertThat(adapter.findGateway("PAYPAL")).isEmpty();
        assertThat(adapter.findGateway(null)).isEmpty();
        assertThat(adapter.findGateway("STRIPE").orElseThrow().supportsCurrency(null)).isFalse();
    }

    @Test
    @DisplayName("Aprobado: referencia GATEWAY-nonce-firma auténtica solo para la misma cuenta, plan, importe, moneda y pasarela")
    void approvedReferenceIsBoundToItsContext() {
        GatewayChargeResult result = charge("STRIPE", SubscriptionPlan.PRO, "tok_visa_ok");
        assertThat(result.status()).isEqualTo(GatewayChargeStatus.APPROVED);
        assertThat(result.approved()).isTrue();
        assertThat(result.commission()).isEqualByComparingTo("4.60"); // 99.90 * 3.60% = 3.60 + 1.00
        assertThat(result.authorizationCode()).startsWith("AUTH-");
        String ref = result.transactionReference();
        assertThat(ref).matches("STRIPE-[0-9a-f]{32}-[0-9a-f]{40}");
        BigDecimal fee = SubscriptionPlan.PRO.monthlyFee();
        assertThat(adapter.isAuthentic(ref, "stripe", account, SubscriptionPlan.PRO, fee, "PEN")).isTrue();
        assertThat(adapter.isAuthentic(ref, "CULQI", account, SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic(ref, "STRIPE", UUID.randomUUID(), SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic(ref, "STRIPE", account, SubscriptionPlan.PREMIUM, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic(ref, "STRIPE", account, SubscriptionPlan.PRO, new BigDecimal("0.01"), "PEN")).isFalse();
        assertThat(adapter.isAuthentic(ref, "STRIPE", account, SubscriptionPlan.PRO, fee, "USD")).isFalse();
        assertThat(adapter.isAuthentic(ref.substring(0, ref.length() - 1) + (ref.endsWith("0") ? "1" : "0"), "STRIPE", account,
                SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(new SandboxPaymentGatewayAdapter("another-sandbox-secret-with-at-least-32-bytes!")
                .isAuthentic(ref, "STRIPE", account, SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic("TX-123", "STRIPE", account, SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic(null, "STRIPE", account, SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(adapter.isAuthentic(ref, null, account, SubscriptionPlan.PRO, fee, "PEN")).isFalse();
        assertThat(charge("STRIPE", SubscriptionPlan.PRO, "tok_visa_ok").transactionReference()).as("nonce aleatorio").isNotEqualTo(ref);
    }

    @Test
    @DisplayName("Rechazos: fondos insuficientes y tarjeta rechazada, sin referencia")
    void declines() {
        GatewayChargeResult funds = charge("IZIPAY", SubscriptionPlan.PREMIUM, "tok_declined_insufficient_funds");
        assertThat(funds.status()).isEqualTo(GatewayChargeStatus.REJECTED);
        assertThat(funds.declineCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(funds.transactionReference()).isNull();
        assertThat(funds.approved()).isFalse();
        GatewayChargeResult card = charge("CULQI", SubscriptionPlan.PREMIUM, "tok_DECLINED_stolen");
        assertThat(card.declineCode()).isEqualTo("CARD_DECLINED");
        assertThat(card.commission()).isEqualByComparingTo("1.72");
        assertThat(charge("CULQI", SubscriptionPlan.PREMIUM, null).approved()).isTrue();
        assertThatThrownBy(() -> charge("PAYPAL", SubscriptionPlan.PREMIUM, "tok")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Configuración y planes: secreto mínimo de 32 bytes; planes con tarifa oficial")
    void configurationAndPlans() {
        assertThatThrownBy(() -> new SandboxPaymentGatewayAdapter("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SandboxPaymentGatewayAdapter(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SubscriptionPlan.parse(" premium ")).isEqualTo(SubscriptionPlan.PREMIUM);
        assertThat(SubscriptionPlan.BASIC.requiresPayment()).isFalse();
        assertThat(SubscriptionPlan.PRO.monthlyFee()).isEqualByComparingTo("99.90");
        assertThatThrownBy(() -> SubscriptionPlan.parse("GOLD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriptionPlan.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriptionPlan.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
