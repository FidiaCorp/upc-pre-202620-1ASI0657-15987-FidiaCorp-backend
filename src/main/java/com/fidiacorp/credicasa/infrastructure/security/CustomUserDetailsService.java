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
 * Provee los usuarios del sistema CrediCasa con contrasenas encriptadas con BCrypt:
 * - ROLE_CLIENT: Comprador de vivienda buscando simular y evaluar su credito hipotecario.
 * - ROLE_REALTOR: Asesor inmobiliario en sala de ventas gestionando cotizaciones con clientes.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final Map<String, String> userPasswords = new HashMap<>();
    private final Map<String, List<SimpleGrantedAuthority>> userRoles = new HashMap<>();

    public CustomUserDetailsService(PasswordEncoder passwordEncoder) {
        userPasswords.put("cliente@credicasa.pe", passwordEncoder.encode("Cliente123!"));
        userRoles.put("cliente@credicasa.pe", Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENT")));

        userPasswords.put("asesor@credicasa.pe", passwordEncoder.encode("Asesor123!"));
        userRoles.put("asesor@credicasa.pe", Collections.singletonList(new SimpleGrantedAuthority("ROLE_REALTOR")));

        userPasswords.put("admin@credicasa.pe", passwordEncoder.encode("Admin123!"));
        userRoles.put("admin@credicasa.pe", List.of(
                new SimpleGrantedAuthority("ROLE_CLIENT"),
                new SimpleGrantedAuthority("ROLE_REALTOR")
        ));
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String key = email != null ? email.toLowerCase().trim() : "";
        String password = userPasswords.get(key);
        List<SimpleGrantedAuthority> authorities = userRoles.get(key);

        if (password == null || authorities == null) {
            throw new UsernameNotFoundException("Usuario no encontrado con correo: " + email);
        }

        return new User(key, password, authorities);
    }
}
