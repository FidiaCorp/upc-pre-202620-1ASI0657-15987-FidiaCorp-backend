package com.fidiacorp.credicasa.identity.domain.model;
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() { super("El correo electrónico ya está registrado."); }
}
