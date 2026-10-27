package com.fidiacorp.credicasa.domain.model;
public class PropertyNotFoundException extends RuntimeException {
    public PropertyNotFoundException() {super("El inmueble no existe o fue eliminado.");}
}
