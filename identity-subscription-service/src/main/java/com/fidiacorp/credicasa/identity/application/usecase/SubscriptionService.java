package com.fidiacorp.credicasa.identity.application.usecase;

import com.fidiacorp.credicasa.identity.application.dto.request.CheckoutRequest;
import com.fidiacorp.credicasa.identity.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.identity.application.dto.response.CheckoutResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.PaymentGatewayResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.PaymentVerificationResponse;
import com.fidiacorp.credicasa.identity.application.dto.response.SubscriptionStatusResponse;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeRequest;
import com.fidiacorp.credicasa.identity.domain.model.GatewayChargeResult;
import com.fidiacorp.credicasa.identity.domain.model.PaymentGateway;
import com.fidiacorp.credicasa.identity.domain.model.PaymentRecord;
import com.fidiacorp.credicasa.identity.domain.model.PaymentRejectedException;
import com.fidiacorp.credicasa.identity.domain.model.PaymentStatus;
import com.fidiacorp.credicasa.identity.domain.model.Subscription;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionPlan;
import com.fidiacorp.credicasa.identity.domain.model.SubscriptionStatus;
import com.fidiacorp.credicasa.identity.domain.ports.out.PaymentGatewayPort;
import com.fidiacorp.credicasa.identity.domain.ports.out.PaymentRecordRepositoryPort;
import com.fidiacorp.credicasa.identity.domain.ports.out.SubscriptionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * US05 (selección de pasarela + checkout sandbox) y US06 (verificación de pago y activación).
 * Cierra la auto-activación gratuita (tarifa oficial + referencia firmada por la pasarela) y el IDOR de comprobantes.
 */
@Service
@Transactional
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final PaymentRecordRepositoryPort paymentRepository;
    private final PaymentGatewayPort gateways;

    public SubscriptionService(SubscriptionRepositoryPort subscriptionRepository, PaymentRecordRepositoryPort paymentRepository,
                               PaymentGatewayPort gateways) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.gateways = gateways;
    }

    @Transactional(readOnly = true)
    public List<PaymentGatewayResponse> listGateways() {
        Map<String, String> fees = new LinkedHashMap<>();
        Arrays.stream(SubscriptionPlan.values()).forEach(p -> fees.put(p.name(), p.monthlyFee().toPlainString()));
        return gateways.availableGateways().stream()
                .map(g -> new PaymentGatewayResponse(g.code(), g.displayName(), g.currencies(), g.commissionPercent(), g.fixedFee(),
                        g.sandbox(), fees))
                .toList();
    }

    public CheckoutResponse checkout(UUID accountId, CheckoutRequest request) {
        SubscriptionPlan plan = SubscriptionPlan.parse(request.planName());
        if (!plan.requiresPayment()) throw new IllegalArgumentException("El plan BASIC es gratuito y no requiere pago.");
        String currency = request.currency() == null || request.currency().isBlank()
                ? SubscriptionPlan.CURRENCY : request.currency().trim().toUpperCase(Locale.ROOT);
        if (!SubscriptionPlan.CURRENCY.equals(currency)) throw new IllegalArgumentException("Las tarifas oficiales se cobran en PEN.");
        String gatewayCode = request.gatewayCode().trim().toUpperCase(Locale.ROOT);
        PaymentGateway gateway = gateways.findGateway(gatewayCode)
                .orElseThrow(() -> new IllegalArgumentException("Pasarela no soportada: " + request.gatewayCode()));
        if (!gateway.supportsCurrency(currency)) throw new IllegalArgumentException("La pasarela " + gatewayCode + " no opera en " + currency + ".");
        GatewayChargeResult result = gateways.charge(new GatewayChargeRequest(gatewayCode, accountId, plan, plan.monthlyFee(), currency,
                request.cardToken()));
        if (!result.approved()) log.info("[PAYMENT] Cobro sandbox rechazado ({}) por {} para la cuenta {}", result.declineCode(), gatewayCode, accountId);
        return CheckoutResponse.from(plan.name(), result);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getSubscriptionStatus(UUID accountId) {
        long payments = paymentRepository.countCompletedByAccountId(accountId);
        Optional<Subscription> latest = subscriptionRepository.findLatestByAccountId(accountId);
        if (latest.isEmpty()) {
            return new SubscriptionStatusResponse(null, accountId, "FREE_TIER", SubscriptionStatus.PENDING, false, null, null, false,
                    "Sin suscripción activa. Elige una pasarela y un plan para acceder a funcionalidades Premium o Pro.", payments);
        }
        Subscription sub = latest.get();
        boolean active = sub.isActive();
        String description = active ? "Suscripción " + sub.getPlanName() + " activa hasta el " + sub.getEndDate() + "."
                : "Suscripción vencida o inactiva.";
        return new SubscriptionStatusResponse(sub.getId(), sub.getAccountId(), sub.getPlanName(), sub.getStatus(), active,
                sub.getStartDate(), sub.getEndDate(), sub.isAutoRenew(), description, payments);
    }

    public PaymentVerificationResponse verifyPaymentAndActivateSubscription(UUID accountId, VerifyPaymentRequest request) {
        String reference = request.transactionReference().trim();
        Optional<PaymentRecord> existing = paymentRepository.findByTransactionReference(reference);
        if (existing.isPresent()) {
            PaymentRecord prev = existing.get();
            if (!prev.getAccountId().equals(accountId)) {
                log.warn("[SECURITY-AUDIT] BOLA bloqueado: cuenta {} intentó reutilizar la referencia de pago de otra cuenta", accountId);
                throw new IllegalStateException("La referencia de pago ya fue utilizada.");
            }
            Subscription sub = subscriptionRepository.findById(prev.getSubscriptionId()).orElseThrow();
            return response(prev, sub, "Transacción previamente verificada y procesada.");
        }

        SubscriptionPlan plan = SubscriptionPlan.parse(request.planName());
        if (!plan.requiresPayment()) throw new IllegalArgumentException("El plan BASIC es gratuito y no requiere verificación de pago.");
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        if (!SubscriptionPlan.CURRENCY.equals(currency)) throw new PaymentRejectedException("La moneda no coincide con la tarifa oficial (PEN).");
        if (request.amount().compareTo(plan.monthlyFee()) != 0) {
            log.warn("[SECURITY-AUDIT] Monto {} no coincide con la tarifa {} del plan {} (cuenta {})", request.amount(), plan.monthlyFee(), plan, accountId);
            throw new PaymentRejectedException("El monto no coincide con la tarifa oficial del plan " + plan.name() + " (" + plan.monthlyFee().toPlainString() + " PEN).");
        }
        String method = request.paymentMethod().trim().toUpperCase(Locale.ROOT);
        if (!gateways.isAuthentic(reference, method, accountId, plan, plan.monthlyFee(), currency)) {
            log.warn("[SECURITY-AUDIT] Referencia de pago no emitida por la pasarela {} para la cuenta {}", method, accountId);
            throw new PaymentRejectedException("La referencia de pago no fue emitida ni aprobada por la pasarela indicada.");
        }

        LocalDate start = LocalDate.now();
        Subscription savedSub = subscriptionRepository.save(new Subscription(UUID.randomUUID(), accountId, plan.name(),
                SubscriptionStatus.ACTIVE, start, start.plusMonths(1), true, LocalDateTime.now()));
        PaymentRecord savedPayment = paymentRepository.save(new PaymentRecord(UUID.randomUUID(), savedSub.getId(), accountId,
                plan.monthlyFee(), currency, PaymentStatus.COMPLETED, reference, method, LocalDateTime.now()));
        return response(savedPayment, savedSub, "Pago verificado satisfactoriamente. Suscripción " + plan.name()
                + " activada hasta " + savedSub.getEndDate() + ".");
    }

    private PaymentVerificationResponse response(PaymentRecord p, Subscription s, String message) {
        return new PaymentVerificationResponse(p.getId(), s.getId(), p.getTransactionReference(), p.getAmount(), p.getCurrency(),
                p.getPaymentMethod(), s.getPlanName(), p.getStatus(), s.getStatus(), s.getEndDate(), p.getProcessedAt(), message);
    }
}
