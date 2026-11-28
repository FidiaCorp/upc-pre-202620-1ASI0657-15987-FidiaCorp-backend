package com.fidiacorp.credicasa.catalog.application.dto.response;
import java.util.List;
public record PropertyPageResponse(List<PropertyResponse> items,int page,int size,long totalElements,long totalPages) {}
