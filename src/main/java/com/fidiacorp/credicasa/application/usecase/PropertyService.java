package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.application.dto.request.CreatePropertyRequest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.PropertyRepositoryPort;
import com.fidiacorp.credicasa.domain.service.PropertyTags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.util.*;
@Service
public class PropertyService {
    private final PropertyRepositoryPort repository;
    private final Clock clock;
    public PropertyService(PropertyRepositoryPort repository,Clock clock) {this.repository=repository;this.clock=clock;}
    @Transactional
    public PropertyResponse create(UUID ownerId,CreatePropertyRequest request) {
        return PropertyResponse.from(repository.save(new Property(UUID.randomUUID(),ownerId,request.title(),request.address(),
            request.description(),request.price(),request.currency(),PropertyTags.normalize(request.tags()),clock.instant())));
    }
    @Transactional(readOnly=true)
    public PropertyResponse get(UUID id) {return PropertyResponse.from(requireActive(id));}
    @Transactional(readOnly=true)
    public PropertyPageResponse search(UUID userId,String query,List<String> tags,boolean mine,int page,int size) {
        if(query.length()>120 || page<0 || size<1 || size>100)throw new IllegalArgumentException("Parámetros de búsqueda inválidos.");
        PropertyPage result=repository.search(new PropertySearch(mine?userId:null,query.strip().toLowerCase(Locale.ROOT),
            PropertyTags.normalize(tags),page,size));
        long totalPages=(result.totalElements()+size-1)/size;
        return new PropertyPageResponse(result.items().stream().map(PropertyResponse::from).toList(),page,size,result.totalElements(),totalPages);
    }
    @Transactional
    public void delete(UUID ownerId,UUID id) {
        Property property=requireActive(id);
        if(!property.ownerId().equals(ownerId))throw new AccessDeniedException("Solo el propietario puede eliminar el inmueble.");
        if(!repository.softDelete(id,ownerId,clock.instant()))throw new PropertyNotFoundException();
    }
    private Property requireActive(UUID id) {return repository.findActiveById(id).orElseThrow(PropertyNotFoundException::new);}
}
