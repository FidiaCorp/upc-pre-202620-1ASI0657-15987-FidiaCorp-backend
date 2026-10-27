package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.CreditOfferRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.CreditOfferJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.CreditOfferJpaRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class CreditOfferRepositoryAdapter implements CreditOfferRepositoryPort {
    private final CreditOfferJpaRepository repository;
    public CreditOfferRepositoryAdapter(CreditOfferJpaRepository repository) {this.repository=repository;}
    public CreditOffer save(CreditOffer o) {
        CreditOfferJpaEntity e=new CreditOfferJpaEntity();e.id=o.id();e.recipientId=o.recipientId();e.issuerId=o.issuerId();
        e.bankCode=o.bankCode();e.origin=o.origin();e.propertyId=o.property().id();e.propertyTitle=o.property().title();
        e.propertyAddress=o.property().address();e.propertyPrice=o.property().price();e.propertyCurrency=o.property().currency();
        e.loanAmount=o.loanAmount();e.currency=o.currency();e.annualInterestRate=o.annualInterestRate();e.termMonths=o.termMonths();
        e.startDate=o.startDate();e.simulationId=o.simulationId();e.createdAt=o.createdAt();return map(repository.saveAndFlush(e));
    }
    public Optional<CreditOffer> findForRecipient(UUID id,UUID recipient) {return repository.findByIdAndRecipientId(id,recipient).map(this::map);}
    public CreditOfferPage findReceived(UUID recipient,int page,int size) {
        var found=repository.findByRecipientId(recipient,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));
        return new CreditOfferPage(found.getContent().stream().map(this::map).toList(),found.getTotalElements());
    }
    private CreditOffer map(CreditOfferJpaEntity e) {
        return new CreditOffer(e.id,e.recipientId,e.issuerId,e.bankCode,e.origin,
            new OfferPropertySnapshot(e.propertyId,e.propertyTitle,e.propertyAddress,e.propertyPrice,e.propertyCurrency),
            e.loanAmount,e.currency,e.annualInterestRate,e.termMonths,e.startDate,e.simulationId,e.createdAt);
    }
}
