package com.fidiacorp.credicasa.domain.model;
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() { super("El correo electrónico ya está registrado."); }
}
