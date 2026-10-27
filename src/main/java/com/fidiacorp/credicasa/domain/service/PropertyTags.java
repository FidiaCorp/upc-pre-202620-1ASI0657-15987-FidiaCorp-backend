package com.fidiacorp.credicasa.domain.service;
import java.text.Normalizer;
import java.util.*;
public final class PropertyTags {
    private PropertyTags() {}
    public static Set<String> normalize(Collection<String> tags) {
        if(tags==null)return Set.of();
        if(tags.size()>20)throw new IllegalArgumentException("Se admiten como máximo 20 etiquetas.");
        SortedSet<String> normalized=new TreeSet<>();
        for(String tag:tags) {
            if(tag==null)throw new IllegalArgumentException("Las etiquetas no pueden estar vacías.");
            String value=Normalizer.normalize(tag,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .strip().replaceAll("(?U)\\s+"," ").strip();
            if(value.isBlank() || value.length()>40)
                throw new IllegalArgumentException("Cada etiqueta debe tener entre 1 y 40 caracteres.");
            normalized.add(value);
        }
        return Collections.unmodifiableSortedSet(normalized);
    }
}
