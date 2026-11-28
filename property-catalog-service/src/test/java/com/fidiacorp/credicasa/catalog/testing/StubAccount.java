package com.fidiacorp.credicasa.catalog.testing;

import java.util.UUID;

/** Cuenta del doble de prueba de identity-subscription-service (campos públicos como la antigua entidad JPA). */
public class StubAccount {
    public UUID id;
    public String firstName;
    public String lastName;
    public String email;
    public String password;
    public String roles = "ROLE_CLIENT";
}
