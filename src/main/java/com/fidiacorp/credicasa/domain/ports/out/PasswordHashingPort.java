package com.fidiacorp.credicasa.domain.ports.out;
public interface PasswordHashingPort {
    String hash(String password);
    boolean matches(String password,String hash);
}
