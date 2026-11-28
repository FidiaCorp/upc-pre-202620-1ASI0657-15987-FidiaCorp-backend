package com.fidiacorp.credicasa.identity.domain.ports.out;
public interface PasswordHashingPort {
    String hash(String password);
    boolean matches(String password,String hash);
}
