package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.application.dto.response.SimulationResponseDto;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.domain.ports.out.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
@Service
public class OfferScheduleService {
    private final CreditOfferRepositoryPort offers;
    private final AccountRepositoryPort accounts;
    private final CalculateCreditSimulationService simulations;
    public OfferScheduleService(CreditOfferRepositoryPort offers,AccountRepositoryPort accounts,CalculateCreditSimulationService simulations) {
        this.offers=offers;this.accounts=accounts;this.simulations=simulations;
    }
    @Transactional
    public SimulationResponseDto generate(UUID recipientId,UUID offerId) {
        CreditOffer offer=offers.lockForRecipient(offerId,recipientId).orElseThrow(OfferResourceNotFoundException::new);
        if(offer.simulationId()!=null)return simulations.getById(recipientId,offer.simulationId());
        Account recipient=accounts.findById(recipientId).orElseThrow(OfferResourceNotFoundException::new);
        ClientProfile profile=new ClientProfile(recipient.id(),null,recipient.firstName()+" "+recipient.lastName(),recipient.email(),BigDecimal.ZERO,null);
        OfferPropertySnapshot snapshot=offer.property();
        CalculateScheduleCommand command=new CalculateScheduleCommand(offer.loanAmount(),offer.currency(),offer.termMonths(),
            offer.annualInterestRate(),GracePeriod.none(),BalloonPayment.none(),BigDecimal.ZERO,BigDecimal.ZERO,snapshot.price(),
            BigDecimal.ZERO,offer.startDate(),profile,new PropertySnapshot(snapshot.id(),snapshot.price(),snapshot.currency(),snapshot.address()),
            offer.annualInterestRate(),recipientId);
        Quotation saved=simulations.calculateAndIssue(command);
        offers.linkSimulation(offerId,recipientId,saved.getId());
        // Return persisted values so POST, GET and repeated requests have the same decimal scales.
        return simulations.getById(recipientId,saved.getId());
    }
    @Transactional(readOnly=true)
    public SimulationResponseDto get(UUID recipientId,UUID offerId) {
        CreditOffer offer=offers.findForRecipient(offerId,recipientId).orElseThrow(OfferResourceNotFoundException::new);
        if(offer.simulationId()==null)throw new QuotationNotFoundException();
        return simulations.getById(recipientId,offer.simulationId());
    }
}
