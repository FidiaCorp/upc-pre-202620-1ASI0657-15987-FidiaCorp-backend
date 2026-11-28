package com.fidiacorp.credicasa.pricing.infrastructure.export;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.*;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.pricing.domain.service.FrenchAmortizationEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US12: el adaptador genera un paquete OpenXML válido (ZIP PK\x03\x04) desde el agregado Quotation. */
class OpenXmlScheduleExportAdapterTest {
    private final OpenXmlScheduleExportAdapter adapter = new OpenXmlScheduleExportAdapter();

    private Quotation quotation() {
        UUID owner = UUID.randomUUID();
        return new FrenchAmortizationEngine().generateQuotation(new CalculateScheduleCommand(new BigDecimal("120000.00"), Currency.PEN, 12,
                new BigDecimal("0.0950"), GracePeriod.of(GracePeriodType.PARTIAL, 2), BalloonPayment.none(), new BigDecimal("0.0005"),
                new BigDecimal("0.00025"), new BigDecimal("150000.00"), new BigDecimal("10.00"), LocalDate.of(2026, 1, 31),
                new ClientProfile(owner, null, "Cliente", "c@example.com", BigDecimal.ZERO, null),
                new PropertySnapshot(UUID.randomUUID(), new BigDecimal("150000.00"), Currency.PEN, "Lima"), null, owner));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return entries;
    }

    @Test
    @DisplayName("Genera un ZIP OpenXML con las 6 partes obligatorias, métricas y una fila por cuota")
    void generatesValidWorkbook() throws Exception {
        Quotation q = quotation();
        byte[] bytes = adapter.export(q, "Cronograma <Prueba> & \"Cía\"");
        assertThat(bytes).startsWith(0x50, 0x4B, 0x03, 0x04);
        Map<String, String> parts = unzip(bytes);
        assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/_rels/workbook.xml.rels", "xl/workbook.xml",
                "xl/styles.xml", "xl/worksheets/sheet1.xml");
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("Cronograma &lt;Prueba&gt; &amp; &quot;Cía&quot;")
                .contains(q.getId().toString()).contains("TCEA").contains("TIR Mensual").contains("PARTIAL").contains("NONE")
                .contains("<v>" + q.getSchedule().get(11).getFinalBalance().toPlainString() + "</v>");
        assertThat(sheet.split("<row ").length - 1).isEqualTo(1 + 9 + 1 + 12);
        assertThat(adapter.mediaType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(adapter.fileExtension()).isEqualTo("xlsx");
    }

    @Test
    @DisplayName("Título vacío usa el título por defecto; métricas ausentes se omiten; null se rechaza")
    void defaultsAndValidation() throws Exception {
        Quotation base = quotation();
        Quotation withoutMetrics = new Quotation(base.getId(), base.getLoanAmount(), base.getCurrency(), base.getTermMonths(),
                base.getAnnualInterestRate(), base.getGracePeriod(), base.getBalloonPayment(), base.getLifeInsuranceRate(),
                base.getPropertyInsuranceRate(), null, base.getMonthlyAdminFee(), base.getStartDate(), base.getClientProfile(),
                base.getPropertySnapshot(), base.getSchedule(), null, base.getStatus(), base.getCreatedAt());
        String sheet = unzip(adapter.export(withoutMetrics, " ")).get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("CREDICASA - CRONOGRAMA DE PAGOS").doesNotContain("TCEA").contains("<t>0</t>");
        assertThat(unzip(adapter.export(base, null)).get("xl/worksheets/sheet1.xml")).contains("CREDICASA - CRONOGRAMA DE PAGOS");
        assertThatThrownBy(() -> adapter.export(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(OpenXmlScheduleExportAdapter.escape(null)).isEmpty();
        assertThat(OpenXmlScheduleExportAdapter.escape("a'b")).isEqualTo("a&apos;b");
    }
}
