package com.fidiacorp.credicasa.quotation.domain.ports.out;

import com.fidiacorp.credicasa.quotation.domain.model.CreditOffer;
import com.fidiacorp.credicasa.quotation.domain.model.CreditOfferPage;

import java.util.Optional;
import java.util.UUID;

public interface CreditOfferRepositoryPort {
    CreditOffer save(CreditOffer offer);
    Optional<CreditOffer> findForRecipient(UUID id, UUID recipientId);
    Optional<CreditOffer> lockForRecipient(UUID id, UUID recipientId);
    /** Emisor o destinatario (BOLA: cualquier otro usuario recibe 404). */
    Optional<CreditOffer> findForParticipant(UUID id, UUID userId);
    /** Bloqueo pesimista para serializar la numeración de versiones. */
    Optional<CreditOffer> lockForIssuer(UUID id, UUID issuerId);
    void linkSimulation(UUID id, UUID recipientId, UUID simulationId);
    CreditOfferPage findReceived(UUID recipientId, int page, int size);
}
