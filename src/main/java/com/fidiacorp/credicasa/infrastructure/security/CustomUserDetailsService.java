package com.fidiacorp.credicasa.infrastructure.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver 1: Seguridad (ASR-SEC) - Control de Roles RBAC
 * Provee los usuarios del sistema CrediCasa con contraseñas encriptadas con BCrypt:
 * - ROLE_CLIENT: Comprador de vivienda buscando simular y evaluar su crédito hipotecario.
 * - ROLE_REALTOR: Asesor inmobiliario en sala de ventas gestionando cotizaciones con clientes.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final Map<String, UserDetails> inMemoryUsers = new HashMap<>();

    public CustomUserDetailsService(PasswordEncoder passwordEncoder) {
        // Usuario 1: ROLE_CLIENT (Comprador de vivienda)
        inMemoryUsers.put("cliente@credicasa.pe", User.builder()
                .username("cliente@credicasa.pe")
                .password(passwordEncoder.encode("Cliente123!"))
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .build());

        // Usuario 2: ROLE_REALTOR (Asesor inmobiliario en sala de ventas)
        inMemoryUsers.put("asesor@credicasa.pe", User.builder()
                .username("asesor@credicasa.pe")
                .password(passwordEncoder.encode("Asesor123!"))
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_REALTOR")))
                .build());

        // Usuario 3: Administrador con ambos roles
        inMemoryUsers.put("admin@credicasa.pe", User.builder()
                .username("admin@credicasa.pe")
                .password(passwordEncoder.encode("Admin123!"))
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_CLIENT"),
                        new SimpleGrantedAuthority("ROLE_REALTOR")
                ))
                .build());
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserDetails user = inMemoryUsers.get(email.toLowerCase().trim());
        if (user == null) {
            throw new UsernameNotFoundException("Usuario no encontrado con correo: " + email);
        }
        return user;
    }
}
