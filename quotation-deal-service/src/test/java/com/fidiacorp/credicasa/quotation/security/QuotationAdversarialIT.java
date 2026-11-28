package com.fidiacorp.credicasa.quotation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.quotation.testing.Downstreams;
import com.fidiacorp.credicasa.quotation.testing.Downstreams.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QA-03 en quotation-deal: V09–V11, V19 (BOLA sobre ofertas, cronogramas, versiones y exportaciones ajenas). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quotation_adversarial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class QuotationAdversarialIT {
    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        Downstreams.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("QA-03 quotation-deal: terceros no leen, generan, versionan ni exportan ofertas ajenas")
    void adversarialVectorsAreRejected() throws Exception {
        Downstreams.reset();
        Actor advisor = Downstreams.actor("advisor", "ROLE_REALTOR");
        Actor victim = Downstreams.actor("victim", "ROLE_CLIENT");
        Actor attacker = Downstreams.actor("attacker", "ROLE_CLIENT");
        Actor rogue = Downstreams.actor("rogue", "ROLE_REALTOR");
        Downstreams.account(victim, "ROLE_CLIENT");
        var property = Downstreams.property(advisor, "250000.00", "PEN");
        String id = json.readTree(mvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + advisor.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("recipientEmail", victim.email(),
                        "propertyId", property, "bankCode", "BCP", "loanAmount", "200000.00", "currency", "PEN",
                        "annualInterestRate", "0.08512345", "termMonths", 12, "startDate", "2026-01-15"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String bearer = "Bearer " + attacker.token();
        mvc.perform(get("/api/v1/offers/" + id).header("Authorization", bearer)).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(victim.email()))));
        mvc.perform(post("/api/v1/offers/" + id + "/schedule").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/" + id + "/versions").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/" + id + "/schedule/export/pdf").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/offers/" + id + "/exports").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/offers/" + id + "/versions").header("Authorization", "Bearer " + rogue.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/offers").header("Authorization", "Bearer " + rogue.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("recipientEmail", victim.email(), "propertyId", property, "bankCode", "BCP",
                        "loanAmount", "200000.00", "currency", "PEN", "annualInterestRate", "0.08", "termMonths", 12, "startDate", "2026-01-15"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/offers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/offers").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(id))));
    }
}
