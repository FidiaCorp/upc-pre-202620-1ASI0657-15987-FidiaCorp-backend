package com.fidiacorp.credicasa.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiGuardFilterTest {
    private MockHttpServletResponse call(InternalApiGuardFilter filter, String uri, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (token != null) request.addHeader(InternalApiGuardFilter.HEADER, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void enforcesSharedTokenOnlyOnInternalRoutes() throws Exception {
        InternalApiGuardFilter filter = new InternalApiGuardFilter("s3cr3t");
        assertThat(filter.enforced()).isTrue();
        assertThat(call(filter, "/api/v1/internal/accounts/x", null).getStatus()).isEqualTo(404);
        assertThat(call(filter, "/api/v1/internal/accounts/x", "wrong").getStatus()).isEqualTo(404);
        var denied = call(filter, "/api/v1/internal/accounts/x", "wrong");
        assertThat(denied.getContentType()).startsWith("application/problem+json");
        assertThat(denied.getContentAsString()).contains("\"status\":404").doesNotContain("accounts");
        assertThat(denied.getErrorMessage()).isNull(); // sin sendError → sin dispatch a /error (que respondería 401)
        assertThat(call(filter, "/api/v1/internal/accounts/x", "s3cr3t").getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/v1/properties", null).getStatus()).isEqualTo(200);
    }

    @Test
    void disabledWhenNoTokenConfigured() throws Exception {
        assertThat(new InternalApiGuardFilter(null).enforced()).isFalse();
        InternalApiGuardFilter open = new InternalApiGuardFilter(" ");
        assertThat(open.enforced()).isFalse();
        assertThat(call(open, "/api/v1/internal/accounts/x", null).getStatus()).isEqualTo(200);
    }

    @Test
    void interceptorAttachesInternalTokenOnlyToInternalRoutes() throws Exception {
        OutboundHeadersInterceptor interceptor = new OutboundHeadersInterceptor("s3cr3t");
        MockClientHttpRequest internal = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://identity:8081/api/v1/internal/accounts/1"));
        interceptor.intercept(internal, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], 200));
        assertThat(internal.getHeaders().getFirst(InternalApiGuardFilter.HEADER)).isEqualTo("s3cr3t");
        MockClientHttpRequest external = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://bank:8090/rates"));
        interceptor.intercept(external, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], 200));
        assertThat(external.getHeaders().containsKey(InternalApiGuardFilter.HEADER)).isFalse();
        MockClientHttpRequest blank = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://identity:8081/api/v1/internal/x"));
        new OutboundHeadersInterceptor(" ").intercept(blank, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], 200));
        assertThat(blank.getHeaders().containsKey(InternalApiGuardFilter.HEADER)).isFalse();
    }
}
