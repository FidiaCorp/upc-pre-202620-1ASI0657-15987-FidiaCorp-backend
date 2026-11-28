package com.fidiacorp.credicasa.shared.web;

import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import com.fidiacorp.credicasa.shared.testapp.KernelTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = KernelTestApplication.class)
@AutoConfigureMockMvc
class ProblemDetailExceptionHandlerIT {
    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "illegal-arg,400", "illegal-state,409", "bad-creds,401", "denied,403", "security,403", "not-found,404", "rule,422",
            "upstream,503", "optimistic,409", "datetime,400", "framework-404,404", "framework-409,409", "framework-503,500", "boom,500"})
    void mapsExceptionsToRfc7807(String kind, int expected) throws Exception {
        mvc.perform(get("/t/" + kind).header(CorrelationId.HEADER, "it-" + kind))
                .andExpect(status().is(expected))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.type").value(containsString("https://api.credicasa.pe/problems/")))
                .andExpect(jsonPath("$.instance").value("/t/" + kind))
                .andExpect(jsonPath("$.correlationId").value("it-" + kind))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(header().string(CorrelationId.HEADER, "it-" + kind))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    void upstreamFailuresAdvertiseRetryAfter() throws Exception {
        mvc.perform(get("/t/upstream")).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"));
        mvc.perform(get("/t/ok")).andExpect(status().isOk()).andExpect(header().exists(CorrelationId.HEADER));
    }

    @Test
    void validationAndBindingErrorsAreBadRequests() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.name").exists());
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("El cuerpo de la solicitud no es válido."));
        mvc.perform(get("/t/typed/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/t/query").param("size", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors").isMap());
        mvc.perform(delete("/t/typed/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void statelessChainAuthenticatesBearerAndRejectsAnonymous() throws Exception {
        mvc.perform(get("/secure/me")).andExpect(status().isUnauthorized());
        UUID id = UUID.randomUUID();
        String token = new JwtTokenIssuer(KernelTestApplication.SECRET, 60_000).issue(id, List.of("ROLE_CLIENT"), null, null);
        mvc.perform(get("/secure/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string(id.toString()))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void helpersTolerateMissingRequestContext() {
        var handler = new ProblemDetailExceptionHandler();
        var generic = handler.handleGeneral(new RuntimeException("x"), null);
        assertThat(generic.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(generic.getBody().getInstance()).isNull();
        var unsupported = handler.handleUnsupportedMethod(new HttpRequestMethodNotSupportedException("PATCH"), new MockHttpServletRequest());
        assertThat(unsupported.getHeaders().containsKey("Allow")).isFalse();
        assertThat(ProblemDetailExceptionHandler.problem(HttpStatus.NOT_FOUND, "x", new MockHttpServletRequest())
                .getProperties()).doesNotContainKey("correlationId");
    }
}
