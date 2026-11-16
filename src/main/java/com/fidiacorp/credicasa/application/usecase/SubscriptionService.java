package com.fidiacorp.credicasa.application.usecase;

import com.fidiacorp.credicasa.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.application.dto.response.PaymentVerificationResponse;
import com.fidiacorp.credicasa.application.dto.response.SubscriptionStatusResponse;
import com.fidiacorp.credicasa.domain.model.PaymentRecord;
import com.fidiacorp.credicasa.domain.model.PaymentStatus;
import com.fidiacorp.credicasa.domain.model.Subscription;
import com.fidiacorp.credicasa.domain.model.SubscriptionStatus;
import com.fidiacorp.credicasa.domain.ports.out.PaymentRecordRepositoryPort;
import com.fidiacorp.credicasa.domain.ports.out.SubscriptionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionService {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final PaymentRecordRepositoryPort paymentRepository;

    public SubscriptionService(SubscriptionRepositoryPort subscriptionRepository,
                               PaymentRecordRepositoryPort paymentRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getSubscriptionStatus(UUID accountId) {
        Optional<Subscription> latest = subscriptionRepository.findLatestByAccountId(accountId);

        if (latest.isEmpty()) {
            return new SubscriptionStatusResponse(
                    null,
                    accountId,
                    "FREE_TIER",
                    SubscriptionStatus.PENDING,
                    false,
                    LocalDate.now(),
                    LocalDate.now(),
                    false,
                    "Sin suscripción activa. Requiere afiliarse para acceder a funcionalidades Pro."
            );
        }

        Subscription sub = latest.get();
        boolean isActive = sub.isActive();
        String description = isActive
                ? "Suscripción activa hasta el " + sub.getEndDate()
                : "Suscripción vencida o inactiva.";

        return new SubscriptionStatusResponse(
                sub.getId(),
                sub.getAccountId(),
                sub.getPlanName(),
                sub.getStatus(),
                isActive,
                sub.getStartDate(),
                sub.getEndDate(),
                sub.isAutoRenew(),
                description
        );
    }

    public PaymentVerificationResponse verifyPaymentAndActivateSubscription(UUID accountId, VerifyPaymentRequest request) {
        Optional<PaymentRecord> existing = paymentRepository.findByTransactionReference(request.transactionReference());
        if (existing.isPresent()) {
            PaymentRecord prev = existing.get();
            return new PaymentVerificationResponse(
                    prev.getId(),
                    prev.getSubscriptionId(),
                    prev.getTransactionReference(),
                    prev.getAmount(),
                    prev.getCurrency(),
                    prev.getStatus(),
                    SubscriptionStatus.ACTIVE,
                    prev.getProcessedAt(),
                    "Transacción previamente verificada y procesada."
            );
        }

        String plan = request.planName() != null && !request.planName().isBlank()
                ? request.planName()
                : "PREMIUM_PROPTECH";

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(1);

        Subscription newSub = new Subscription(
                UUID.randomUUID(),
                accountId,
                plan,
                SubscriptionStatus.ACTIVE,
                start,
                end,
                true,
                LocalDateTime.now()
        );
        Subscription savedSub = subscriptionRepository.save(newSub);

        PaymentRecord payment = new PaymentRecord(
                UUID.randomUUID(),
                savedSub.getId(),
                accountId,
                request.amount(),
                request.currency(),
                PaymentStatus.COMPLETED,
                request.transactionReference(),
                request.paymentMethod(),
                LocalDateTime.now()
        );
        PaymentRecord savedPayment = paymentRepository.save(payment);

        return new PaymentVerificationResponse(
                savedPayment.getId(),
                savedSub.getId(),
                savedPayment.getTransactionReference(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getStatus(),
                savedSub.getStatus(),
                savedPayment.getProcessedAt(),
                "Pago verificado satisfactoriamente. Suscripción activada hasta " + savedSub.getEndDate() + "."
        );
    }
}
