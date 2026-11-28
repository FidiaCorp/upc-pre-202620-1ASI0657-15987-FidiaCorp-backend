package com.fidiacorp.credicasa.quotation.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fidiacorp.credicasa.quotation.application.dto.external.PricingCalculateRequest;
import com.fidiacorp.credicasa.quotation.application.dto.request.CreateQuotationVersionRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.QuotationVersionResponse;
import com.fidiacorp.credicasa.quotation.application.dto.snapshot.QuotationSnapshot;
import com.fidiacorp.credicasa.quotation.application.ports.PricingPort;
import com.fidiacorp.credicasa.quotation.domain.model.CreditOffer;
import com.fidiacorp.credicasa.quotation.domain.model.OfferResourceNotFoundException;
import com.fidiacorp.credicasa.quotation.domain.model.QuotationVersion;
import com.fidiacorp.credicasa.quotation.domain.model.SnapshotHasher;
import com.fidiacorp.credicasa.quotation.domain.ports.out.CreditOfferRepositoryPort;
import com.fidiacorp.credicasa.quotation.domain.ports.out.QuotationVersionRepositoryPort;
import com.fidiacorp.credicasa.shared.events.QuotationVersionIssuedEvent;
import com.fidiacorp.credicasa.shared.web.CorrelationIdFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * US21: versionado inmutable. Cada versión congela términos + cronograma calculado por mortgage-pricing-service en un
 * JSON canónico sellado con SHA-256, con vigencia ({@code expiresAt}). Solo el emisor crea versiones (serializadas
 * con bloqueo pesimista de la oferta); emisor y destinatario las consultan; terceros reciben 404.
 */
@Service
public class QuotationVersionService {
    public static final int DEFAULT_VALIDITY_DAYS = 15;
    private final CreditOfferRepositoryPort offers;
    private final QuotationVersionRepositoryPort versions;
    private final PricingPort pricing;
    private final IntegrationEventRecorder events;
    private final Clock clock;
    private final ObjectMapper canonical;

    public QuotationVersionService(CreditOfferRepositoryPort offers, QuotationVersionRepositoryPort versions, PricingPort pricing,
                                   IntegrationEventRecorder events, Clock clock) {
        this.offers = offers;
        this.versions = versions;
        this.pricing = pricing;
        this.events = events;
        this.clock = clock;
        this.canonical = JsonMapper.builder().addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

    /** Crea la versión 1 al emitir la oferta (misma transacción). */
    @Transactional
    public QuotationVersion issueInitial(CreditOffer offer) {
        return freeze(offer, offer.issuerId(), offer.loanAmount(), offer.annualInterestRate(), offer.termMonths(), DEFAULT_VALIDITY_DAYS, false);
    }

    @Transactional
    public QuotationVersionResponse issueNewVersion(UUID issuerId, UUID offerId, CreateQuotationVersionRequest request) {
        CreditOffer offer = offers.lockForIssuer(offerId, issuerId).orElseThrow(OfferResourceNotFoundException::new);
        QuotationVersion latest = versions.findLatest(offerId).orElseThrow(OfferResourceNotFoundException::new);
        BigDecimal loan = request.loanAmount() == null ? latest.loanAmount() : request.loanAmount();
        BigDecimal rate = request.annualInterestRate() == null ? latest.annualInterestRate() : request.annualInterestRate();
        int term = request.termMonths() == null ? latest.termMonths() : request.termMonths();
        if (loan.compareTo(offer.property().price()) > 0) throw new IllegalArgumentException("El préstamo no puede superar el precio del inmueble.");
        int validity = request.validityDays() == null ? DEFAULT_VALIDITY_DAYS : request.validityDays();
        return response(freeze(offer, issuerId, loan, rate, term, validity, true), true);
    }

    @Transactional(readOnly = true)
    public List<QuotationVersionResponse> list(UUID userId, UUID offerId) {
        offers.findForParticipant(offerId, userId).orElseThrow(OfferResourceNotFoundException::new);
        return versions.findByOffer(offerId).stream().map(v -> response(v, false)).toList();
    }

    @Transactional(readOnly = true)
    public QuotationVersionResponse get(UUID userId, UUID offerId, int versionNumber) {
        offers.findForParticipant(offerId, userId).orElseThrow(OfferResourceNotFoundException::new);
        return response(versions.findByOfferAndNumber(offerId, versionNumber).orElseThrow(OfferResourceNotFoundException::new), true);
    }

    /** Recalcula el SHA-256 del snapshot almacenado (detección de manipulación). */
    @Transactional(readOnly = true)
    public QuotationVersionResponse verify(UUID userId, UUID versionId) {
        QuotationVersion version = versions.findById(versionId).orElseThrow(OfferResourceNotFoundException::new);
        offers.findForParticipant(version.offerId(), userId).orElseThrow(OfferResourceNotFoundException::new);
        return response(version, false);
    }

    /** Versión vigente para exportar (por número o la última). */
    @Transactional(readOnly = true)
    public QuotationVersion resolve(UUID userId, UUID offerId, Integer versionNumber) {
        offers.findForParticipant(offerId, userId).orElseThrow(OfferResourceNotFoundException::new);
        return (versionNumber == null ? versions.findLatest(offerId) : versions.findByOfferAndNumber(offerId, versionNumber))
                .orElseThrow(OfferResourceNotFoundException::new);
    }

    public QuotationSnapshot snapshot(QuotationVersion version) {
        try {
            return canonical.readValue(version.snapshotJson(), QuotationSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot ilegible para la versión " + version.versionNumber(), e);
        }
    }

    private QuotationVersion freeze(CreditOffer offer, UUID issuer, BigDecimal requestedLoan, BigDecimal requestedRate, int term, int validityDays,
                                    boolean notify) {
        // Escalas canónicas (importe 2, tasa 8 decimales) para que el snapshot y su hash no dependan de cómo se escribió la cifra.
        BigDecimal loan = requestedLoan.setScale(2, java.math.RoundingMode.HALF_EVEN);
        BigDecimal rate = requestedRate.setScale(8, java.math.RoundingMode.HALF_EVEN);
        OfferScheduleResponse schedule = pricing.calculate(new PricingCalculateRequest(loan, offer.property().price(), offer.currency(), rate,
                term, offer.startDate(), offer.property().address(), rate));
        int number = versions.nextVersionNumber(offer.id());
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = issuedAt.plus(Duration.ofDays(validityDays));
        QuotationSnapshot snapshot = new QuotationSnapshot(1, offer.id(), number, issuedAt, expiresAt, offer.bankCode().name(),
                offer.bankCode().displayName(), offer.bankCode().demoTitle(), offer.issuerId(), offer.recipientId(),
                new QuotationSnapshot.Property(offer.property().id(), offer.property().title(), offer.property().address(), offer.property().price(),
                        offer.property().currency()),
                new QuotationSnapshot.Terms(loan, offer.currency(), rate, term, offer.startDate()), schedule);
        String jsonSnapshot;
        try {
            jsonSnapshot = canonical.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo congelar el snapshot", e);
        }
        QuotationVersion version = versions.append(new QuotationVersion(UUID.randomUUID(), offer.id(), number, loan, rate, term, jsonSnapshot,
                SnapshotHasher.sha256(jsonSnapshot), issuer, schedule.quotationId(), issuedAt, expiresAt));
        if (notify) {
            events.record(offer.id(), new QuotationVersionIssuedEvent(UUID.randomUUID(), issuedAt, CorrelationIdFilter.current(), offer.id(),
                    version.id(), number, offer.recipientId(), version.snapshotHash(),
                    "Nueva versión " + number + " de su oferta " + offer.bankCode().displayName()));
        }
        return version;
    }

    private QuotationVersionResponse response(QuotationVersion v, boolean withSnapshot) {
        QuotationSnapshot snapshot = snapshot(v);
        OfferScheduleResponse s = snapshot.schedule();
        return new QuotationVersionResponse(v.id(), v.offerId(), v.versionNumber(), snapshot.terms().currency(), v.loanAmount(),
                v.annualInterestRate(), v.termMonths(), s.estimatedMonthlyInstallment(), s.metrics().tcea(), s.metrics().totalInterest(),
                s.metrics().totalCost(), v.snapshotHash(), v.intact(), v.expired(clock.instant()), v.issuedBy(), v.issuedAt(), v.expiresAt(),
                withSnapshot ? snapshot : null);
    }
}
