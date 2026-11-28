package com.fidiacorp.credicasa.quotation.application.dto.response;
import java.util.List;
public record CreditOfferPageResponse(List<CreditOfferResponse> items,int page,int size,long totalElements,long totalPages) {}
