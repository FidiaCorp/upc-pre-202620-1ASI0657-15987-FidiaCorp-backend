package com.fidiacorp.credicasa.quotation.application.usecase;

import com.fidiacorp.credicasa.quotation.application.dto.external.PricingCalculateRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.ports.PricingPort;
import com.fidiacorp.credicasa.quotation.domain.model.CreditOffer;
import com.fidiacorp.credicasa.quotation.domain.model.OfferResourceNotFoundException;
import com.fidiacorp.credicasa.quotation.domain.ports.out.CreditOfferRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * US11 para ofertas recibidas: el destinatario genera (una sola vez, con bloqueo pesimista) su cronograma en
 * mortgage-pricing-service usando SU JWT, y la oferta guarda el {@code simulationId} como referencia lógica.
 */
@Service
public class OfferScheduleService {
    private final CreditOfferRepositoryPort offers;
    private final PricingPort pricing;

    public OfferScheduleService(CreditOfferRepositoryPort offers, PricingPort pricing) {
        this.offers = offers;
        this.pricing = pricing;
    }

    @Transactional
    public OfferScheduleResponse generate(UUID recipientId, UUID offerId) {
        CreditOffer offer = offers.lockForRecipient(offerId, recipientId).orElseThrow(OfferResourceNotFoundException::new);
        if (offer.simulationId() != null) return pricing.get(offer.simulationId());
        OfferScheduleResponse created = pricing.calculate(new PricingCalculateRequest(offer.loanAmount(), offer.property().price(), offer.currency(),
                offer.annualInterestRate(), offer.termMonths(), offer.startDate(), offer.property().address(), offer.annualInterestRate()));
        offers.linkSimulation(offerId, recipientId, created.quotationId());
        return pricing.get(created.quotationId());
    }

    @Transactional(readOnly = true)
    public OfferScheduleResponse get(UUID recipientId, UUID offerId) {
        CreditOffer offer = offers.findForRecipient(offerId, recipientId).orElseThrow(OfferResourceNotFoundException::new);
        if (offer.simulationId() == null) throw new OfferResourceNotFoundException();
        return pricing.get(offer.simulationId());
    }
}
