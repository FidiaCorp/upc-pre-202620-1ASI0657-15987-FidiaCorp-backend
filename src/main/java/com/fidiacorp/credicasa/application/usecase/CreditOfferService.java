package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.application.dto.request.CreateCreditOfferRequest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.util.UUID;
@Service
public class CreditOfferService {
    private final AccountRepositoryPort accounts;
    private final PropertyRepositoryPort properties;
    private final CreditOfferRepositoryPort offers;
    private final OfferNotificationRepositoryPort notifications;
    private final Clock clock;
    public CreditOfferService(AccountRepositoryPort accounts,PropertyRepositoryPort properties,CreditOfferRepositoryPort offers,
        OfferNotificationRepositoryPort notifications,Clock clock) {
        this.accounts=accounts;this.properties=properties;this.offers=offers;this.notifications=notifications;this.clock=clock;
    }
    @Transactional
    public CreditOfferResponse issue(UUID issuerId,CreateCreditOfferRequest request) {
        Account issuer=accounts.findById(issuerId).orElseThrow(OfferResourceNotFoundException::new);
        if(!issuer.roles().contains("ROLE_REALTOR"))throw new AccessDeniedException("La emisión requiere rol de asesor.");
        Account recipient=accounts.findByEmail(request.recipientEmail()).filter(a -> a.roles().contains("ROLE_CLIENT"))
            .orElseThrow(OfferResourceNotFoundException::new);
        Property property=properties.findActiveById(request.propertyId()).orElseThrow(PropertyNotFoundException::new);
        if(!property.ownerId().equals(issuerId))throw new AccessDeniedException("Solo puede emitir ofertas para sus inmuebles.");
        if(request.currency()!=property.currency())throw new IllegalArgumentException("La moneda debe coincidir con la del inmueble.");
        if(request.loanAmount().compareTo(property.price())>0)throw new IllegalArgumentException("El préstamo no puede superar el precio del inmueble.");
        OfferPropertySnapshot snapshot=new OfferPropertySnapshot(property.id(),property.title(),property.address(),property.price(),property.currency());
        CreditOffer saved=offers.save(new CreditOffer(UUID.randomUUID(),recipient.id(),issuerId,request.bankCode(),OfferOrigin.DEMO,snapshot,
            request.loanAmount(),request.currency(),request.annualInterestRate(),request.termMonths(),request.startDate(),null,clock.instant()));
        notifications.save(new OfferNotification(UUID.randomUUID(),saved.id(),recipient.id(),request.bankCode().demoTitle(),saved.createdAt(),null));
        return CreditOfferResponse.from(saved);
    }
    @Transactional(readOnly=true)
    public CreditOfferResponse getReceived(UUID recipientId,UUID id) {
        return CreditOfferResponse.from(offers.findForRecipient(id,recipientId).orElseThrow(OfferResourceNotFoundException::new));
    }
    @Transactional(readOnly=true)
    public CreditOfferPageResponse listReceived(UUID recipientId,int page,int size) {
        validatePage(page,size);CreditOfferPage found=offers.findReceived(recipientId,page,size);
        return new CreditOfferPageResponse(found.items().stream().map(CreditOfferResponse::from).toList(),page,size,
            found.totalElements(),(found.totalElements()+size-1)/size);
    }
    private void validatePage(int page,int size) {if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Paginación inválida.");}
}
