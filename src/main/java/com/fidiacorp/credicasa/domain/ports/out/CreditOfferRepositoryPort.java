package com.fidiacorp.credicasa.domain.ports.out;
import com.fidiacorp.credicasa.domain.model.*;
import java.util.*;
public interface CreditOfferRepositoryPort {
    CreditOffer save(CreditOffer offer);
    Optional<CreditOffer> findForRecipient(UUID id,UUID recipientId);
    Optional<CreditOffer> lockForRecipient(UUID id,UUID recipientId);
    void linkSimulation(UUID id,UUID recipientId,UUID simulationId);
    CreditOfferPage findReceived(UUID recipientId,int page,int size);
}
