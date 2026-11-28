package com.fidiacorp.credicasa.shared.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationAndOutboundTest {
    @AfterEach
    void clear() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void keepsSafeIdsAndReplacesUnsafeOnes() {
        assertThat(CorrelationId.sanitizeOrGenerate("abc-123")).isEqualTo("abc-123");
        assertThat(CorrelationId.sanitizeOrGenerate("bad\r\nheader")).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate(null)).hasSize(36);
        assertThat(CorrelationId.sanitizeOrGenerate("x".repeat(65))).hasSize(36);
    }

    @Test
    void filterPublishesIdInResponseAttributeAndMdcThenCleansUp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(CorrelationId.HEADER, "corr-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        new CorrelationIdFilter().doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(CorrelationIdFilter.current());
            }
        });
        assertThat(seen.get()).isEqualTo("corr-1");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("corr-1");
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo("corr-1");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(CorrelationIdFilter.current()).hasSize(36);
    }

    private MockClientHttpRequest intercept(MockClientHttpRequest outbound) throws Exception {
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], 200);
        new OutboundHeadersInterceptor().intercept(outbound, new byte[0], execution);
        return outbound;
    }

    @Test
    void propagatesCorrelationAndBearerFromIncomingRequest() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "corr-9");
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        MockClientHttpRequest out = intercept(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x")));
        assertThat(out.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("corr-9");
        assertThat(out.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer user-token");
    }

    @Test
    void neverOverridesExplicitHeadersNorForwardsNonBearerCredentials() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "corr-9");
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        MockClientHttpRequest explicit = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x"));
        explicit.getHeaders().set(CorrelationId.HEADER, "own");
        explicit.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer service");
        intercept(explicit);
        assertThat(explicit.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("own");
        assertThat(explicit.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer service");

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader(HttpHeaders.AUTHORIZATION, "Basic x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(basic));
        assertThat(intercept(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x"))).getHeaders()
                .containsKey(HttpHeaders.AUTHORIZATION)).isFalse();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertThat(intercept(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x"))).getHeaders()
                .containsKey(HttpHeaders.AUTHORIZATION)).isFalse();

        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
        MockClientHttpRequest bare = intercept(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x")));
        assertThat(bare.getHeaders().containsKey(CorrelationId.HEADER)).isFalse();
        assertThat(bare.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
