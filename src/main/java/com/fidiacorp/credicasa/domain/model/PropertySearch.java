package com.fidiacorp.credicasa.domain.model;
import java.util.Set;
import java.util.UUID;
public record PropertySearch(UUID ownerId, String query, Set<String> tags, int page, int size) {}
