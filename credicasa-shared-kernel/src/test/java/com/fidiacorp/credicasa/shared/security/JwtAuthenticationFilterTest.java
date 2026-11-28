package com.fidiacorp.credicasa.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    final JwtTokenIssuer issuer = new JwtTokenIssuer(JwtTokenKernelTest.SECRET, 60_000);
    final JwtTokenValidator validator = new JwtTokenValidator(JwtTokenKernelTest.SECRET);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void run(JwtAuthenticationFilter filter, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void validBearerAuthenticatesWithSubjectRolesAndClaims() throws Exception {
        UUID id = UUID.randomUUID();
        String token = issuer.issue(id, List.of("ROLE_REALTOR"), "r@credicasa.pe", "Rita");
        run(new JwtAuthenticationFilter(validator, null), "Bearer " + token);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo(id.toString());
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_REALTOR");
        assertThat(AuthenticatedUser.id((Principal) auth)).isEqualTo(id);
        assertThat(AuthenticatedUser.displayName((Principal) auth)).isEqualTo("Rita");
        assertThat(AuthenticatedUser.email((Principal) auth)).contains("r@credicasa.pe");
    }

    @Test
    void revokedMissingOrMalformedTokensStayAnonymous() throws Exception {
        String token = issuer.issue(UUID.randomUUID(), List.of("ROLE_CLIENT"), null, null);
        run(new JwtAuthenticationFilter(validator, t -> true), "Bearer " + token);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        JwtAuthenticationFilter open = new JwtAuthenticationFilter(validator, RevocationChecker.NONE);
        run(open, null);
        run(open, "Basic abc");
        run(open, "Bearer ");
        run(open, "Bearer garbage");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(RevocationChecker.NONE.isRevoked("x")).isFalse();
    }

    @Test
    void bearerTokenParsing() {
        assertThat(JwtAuthenticationFilter.bearerToken("Bearer abc ")).isEqualTo("abc");
        assertThat(JwtAuthenticationFilter.bearerToken(null)).isNull();
        assertThat(JwtAuthenticationFilter.bearerToken("bearer abc")).isNull();
        assertThat(JwtAuthenticationFilter.bearerToken("Bearer ")).isNull();
    }

    @Test
    void displayNameFallsBackToEmailThenDefault() {
        UUID id = UUID.randomUUID();
        var withEmail = new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());
        withEmail.setDetails(new JwtClaims(id, List.of(), null, null, null, "e@x.pe", null));
        assertThat(AuthenticatedUser.displayName(withEmail)).isEqualTo("e@x.pe");
        var blank = new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());
        blank.setDetails(new JwtClaims(id, List.of(), null, null, null, null, " "));
        assertThat(AuthenticatedUser.displayName(blank)).isEqualTo("Usuario CrediCasa");
        var noClaims = new UsernamePasswordAuthenticationToken(id.toString(), null, List.of());
        assertThat(AuthenticatedUser.claims(noClaims)).isEmpty();
        Principal plain = id::toString;
        assertThat(AuthenticatedUser.claims(plain)).isEmpty();
        assertThat(AuthenticatedUser.displayName(plain)).isEqualTo("Usuario CrediCasa");
        assertThat(AuthenticatedUser.email(plain)).isEmpty();
    }
}
