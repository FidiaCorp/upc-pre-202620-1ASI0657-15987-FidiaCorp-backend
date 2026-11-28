package com.fidiacorp.credicasa.pricing.interfaces.rest;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.pricing.application.dto.request.ClientProfileDto;
import com.fidiacorp.credicasa.pricing.application.dto.request.SimulationRequestDto;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.pricing.testing.StubAccounts;
import com.fidiacorp.credicasa.pricing.infrastructure.persistence.repository.QuotationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:excel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class ScheduleExcelExportIntegrationTest {

    private static final String PASSWORD = "CorrectHorseBattery123!";
    private static final String EMAIL = "excel.client@fidiacorp.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubAccounts accounts;
    @Autowired QuotationJpaRepository quotations;

    private String token;
    private UUID simulationId;

    @BeforeEach
    void setUp() throws Exception {
        quotations.deleteAll();
        accounts.deleteAll();

        // Register client
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", "Jesus",
                        "lastName", "Godoy",
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isCreated());

        // Login
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isOk())
                .andReturn();

        token = json.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        // Calculate simulation
        SimulationRequestDto simReq = new SimulationRequestDto(
                new BigDecimal("180000.00"),
                new BigDecimal("150000.00"),
                Currency.PEN,
                null,
                120,
                new BigDecimal("0.0890"),
                GracePeriodType.NONE,
                0,
                BigDecimal.ZERO,
                null,
                new BigDecimal("0.00035"),
                new BigDecimal("0.00025"),
                new BigDecimal("15.00"),
                new BigDecimal("0.0890"),
                LocalDate.now(),
                "Av. Arequipa 1234, Lima",
                new ClientProfileDto("45678912", "Jesus Godoy", EMAIL, new BigDecimal("4500.00"), null)
        );

        MvcResult simResult = mvc.perform(post("/api/v1/simulations/calculate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(simReq)))
                .andExpect(status().isCreated())
                .andReturn();

        simulationId = UUID.fromString(json.readTree(simResult.getResponse().getContentAsString()).get("quotationId").asText());
    }

    @Test
    @DisplayName("US12: Descargar cronograma de pagos en archivo OpenXML .xlsx valido")
    void testExportSimulationToXlsx() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/simulations/" + simulationId + "/export/xlsx")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] xlsxBytes = result.getResponse().getContentAsByteArray();
        assertThat(xlsxBytes).isNotEmpty();

        // Verify valid ZIP structure containing OpenXML parts
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        assertThat(entries).contains(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/_rels/workbook.xml.rels",
                "xl/workbook.xml",
                "xl/worksheets/sheet1.xml",
                "xl/styles.xml"
        );
    }
}
