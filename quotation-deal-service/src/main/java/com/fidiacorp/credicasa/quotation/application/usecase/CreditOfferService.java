package com.fidiacorp.credicasa.quotation.application.usecase;

import com.fidiacorp.credicasa.quotation.application.dto.external.AccountSummary;
import com.fidiacorp.credicasa.quotation.application.dto.external.PropertySummary;
import com.fidiacorp.credicasa.quotation.application.dto.request.CreateCreditOfferRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.CreditOfferPageResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.CreditOfferResponse;
import com.fidiacorp.credicasa.quotation.application.ports.IdentityDirectoryPort;
import com.fidiacorp.credicasa.quotation.application.ports.PropertyCatalogPort;
import com.fidiacorp.credicasa.quotation.domain.model.CreditOffer;
import com.fidiacorp.credicasa.quotation.domain.model.CreditOfferPage;
import com.fidiacorp.credicasa.quotation.domain.model.OfferOrigin;
import com.fidiacorp.credicasa.quotation.domain.model.OfferPropertySnapshot;
import com.fidiacorp.credicasa.quotation.domain.model.OfferResourceNotFoundException;
import com.fidiacorp.credicasa.quotation.domain.ports.out.CreditOfferRepositoryPort;
import com.fidiacorp.credicasa.shared.error.ResourceNotFoundException;
import com.fidiacorp.credicasa.shared.events.CreditOfferIssuedEvent;
import com.fidiacorp.credicasa.shared.web.CorrelationIdFilter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * US10: emisión DEMO de ofertas. Valida destinatario (identity) e inmueble propio (catalog) vía API interna, guarda la
 * oferta, congela la versión 1 (US21) y registra {@code credit.offer.issued.v1} en el outbox en una sola transacción.
 */
@Service
public class CreditOfferService {
    private final IdentityDirectoryPort identity;
    private final PropertyCatalogPort catalog;
    private final CreditOfferRepositoryPort offers;
    private final QuotationVersionService versions;
    private final IntegrationEventRecorder events;
    private final Clock clock;

    public CreditOfferService(IdentityDirectoryPort identity, PropertyCatalogPort catalog, CreditOfferRepositoryPort offers,
                              QuotationVersionService versions, IntegrationEventRecorder events, Clock clock) {
        this.identity = identity;
        this.catalog = catalog;
        this.offers = offers;
        this.versions = versions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CreditOfferResponse issue(UUID issuerId, CreateCreditOfferRequest request) {
        AccountSummary recipient = identity.findByEmail(request.recipientEmail()).filter(AccountSummary::isClient)
                .orElseThrow(OfferResourceNotFoundException::new);
        PropertySummary property = catalog.findActive(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("El inmueble no existe o fue eliminado."));
        if (!property.ownerId().equals(issuerId)) throw new AccessDeniedException("Solo puede emitir ofertas para sus inmuebles.");
        if (request.currency() != property.currency()) throw new IllegalArgumentException("La moneda debe coincidir con la del inmueble.");
        if (request.loanAmount().compareTo(property.price()) > 0) throw new IllegalArgumentException("El préstamo no puede superar el precio del inmueble.");
        OfferPropertySnapshot snapshot = new OfferPropertySnapshot(property.id(), property.title(), property.address(), property.price(), property.currency());
        CreditOffer saved = offers.save(new CreditOffer(UUID.randomUUID(), recipient.id(), issuerId, request.bankCode(), OfferOrigin.DEMO, snapshot,
                request.loanAmount(), request.currency(), request.annualInterestRate(), request.termMonths(), request.startDate(), null,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        versions.issueInitial(saved);
        events.record(saved.id(), new CreditOfferIssuedEvent(UUID.randomUUID(), saved.createdAt(), CorrelationIdFilter.current(), saved.id(),
                recipient.id(), issuerId, request.bankCode().name(), request.bankCode().demoTitle(), saved.loanAmount(), saved.currency().name()));
        return CreditOfferResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CreditOfferResponse getReceived(UUID recipientId, UUID id) {
        return CreditOfferResponse.from(offers.findForRecipient(id, recipientId).orElseThrow(OfferResourceNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public CreditOfferPageResponse listReceived(UUID recipientId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginación inválida.");
        CreditOfferPage found = offers.findReceived(recipientId, page, size);
        return new CreditOfferPageResponse(found.items().stream().map(CreditOfferResponse::from).toList(), page, size,
                found.totalElements(), (found.totalElements() + size - 1) / size);
    }
}
