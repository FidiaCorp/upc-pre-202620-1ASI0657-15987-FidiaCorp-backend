package com.fidiacorp.credicasa.domain.service;

import com.fidiacorp.credicasa.application.dto.response.InstallmentDto;
import com.fidiacorp.credicasa.application.dto.response.SimulationResponseDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExcelScheduleExportEngine {

    public byte[] generateExcel(SimulationResponseDto simulation, String title) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", buildContentTypesXml());

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", buildGlobalRelsXml());

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml());

            // 4. xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", buildWorkbookXml());

            // 5. xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", buildStylesXml());

            // 6. xl/worksheets/sheet1.xml
            addZipEntry(zos, "xl/worksheets/sheet1.xml", buildSheetXml(simulation, title));

            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar archivo Excel del cronograma", e);
        }
    }

    private void addZipEntry(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String buildContentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n" +
                "  <Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" +
                "  <Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n" +
                "</Types>";
    }

    private String buildGlobalRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>\n" +
                "</Relationships>";
    }

    private String buildWorkbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n" +
                "  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n" +
                "</Relationships>";
    }

    private String buildWorkbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n" +
                "  <sheets>\n" +
                "    <sheet name=\"Cronograma\" sheetId=\"1\" r:id=\"rId1\"/>\n" +
                "  </sheets>\n" +
                "</workbook>";
    }

    private String buildStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n" +
                "  <fonts count=\"2\">\n" +
                "    <font><name val=\"Calibri\"/><sz val=\"11\"/></font>\n" +
                "    <font><b/><name val=\"Calibri\"/><sz val=\"11\"/></font>\n" +
                "  </fonts>\n" +
                "  <fills count=\"2\">\n" +
                "    <fill><patternFill patternType=\"none\"/></fill>\n" +
                "    <fill><patternFill patternType=\"gray125\"/></fill>\n" +
                "  </fills>\n" +
                "  <borders count=\"1\">\n" +
                "    <border><left/><right/><top/><bottom/></border>\n" +
                "  </borders>\n" +
                "  <cellXfs count=\"2\">\n" +
                "    <xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>\n" +
                "    <xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" applyFont=\"1\"/>\n" +
                "  </cellXfs>\n" +
                "</styleSheet>";
    }

    private String buildSheetXml(SimulationResponseDto sim, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");
        sb.append("  <sheetData>\n");

        int row = 1;
        // Title
        sb.append("    <row r=\"").append(row).append("\">\n");
        sb.append("      <c r=\"A").append(row).append("\" t=\"inlineStr\" s=\"1\"><is><t>")
                .append(escapeXml(title != null ? title : "CREDICASA - CRONOGRAMA DE PAGOS"))
                .append("</t></is></c>\n");
        sb.append("    </row>\n");
        row++;

        // Summary Info
        row = addSummaryRow(sb, row, "ID Cotizacion", sim.quotationId() != null ? sim.quotationId().toString() : "N/A");
        row = addSummaryRow(sb, row, "Moneda", sim.currency() != null ? sim.currency().name() : "PEN");
        row = addSummaryRow(sb, row, "Monto del Prestamo", sim.loanAmount() != null ? sim.loanAmount().toPlainString() : "0.00");
        row = addSummaryRow(sb, row, "Valor del Inmueble", sim.propertyValue() != null ? sim.propertyValue().toPlainString() : "0.00");
        row = addSummaryRow(sb, row, "Plazo (meses)", String.valueOf(sim.termMonths()));
        if (sim.metrics() != null) {
            row = addSummaryRow(sb, row, "TCEA", sim.metrics().tcea() != null ? sim.metrics().tcea().toPlainString() : "N/A");
            row = addSummaryRow(sb, row, "TEA", sim.metrics().tea() != null ? sim.metrics().tea().toPlainString() : "N/A");
            row = addSummaryRow(sb, row, "VAN", sim.metrics().van() != null ? sim.metrics().van().toPlainString() : "N/A");
            row = addSummaryRow(sb, row, "TIR Mensual", sim.metrics().monthlyTir() != null ? sim.metrics().monthlyTir().toPlainString() : "N/A");
        }
        row++; // empty row

        // Headers
        sb.append("    <row r=\"").append(row).append("\">\n");
        String[] headers = {
                "Nro Cuota", "Fecha Vencimiento", "Saldo Inicial", "Amortizacion",
                "Interes", "Seguro Desgravamen", "Seguro Inmueble", "Gastos Admin",
                "Cuota Total", "Saldo Final", "Periodo Gracia"
        };
        char col = 'A';
        for (String h : headers) {
            sb.append("      <c r=\"").append(col).append(row).append("\" t=\"inlineStr\" s=\"1\"><is><t>")
                    .append(escapeXml(h)).append("</t></is></c>\n");
            col++;
        }
        sb.append("    </row>\n");
        row++;

        // Data rows
        if (sim.schedule() != null) {
            for (InstallmentDto inst : sim.schedule()) {
                sb.append("    <row r=\"").append(row).append("\">\n");
                sb.append("      <c r=\"A").append(row).append("\"><v>").append(inst.installmentNumber()).append("</v></c>\n");
                sb.append("      <c r=\"B").append(row).append("\" t=\"inlineStr\"><is><t>").append(inst.dueDate() != null ? inst.dueDate().toString() : "").append("</t></is></c>\n");
                sb.append("      <c r=\"C").append(row).append("\"><v>").append(inst.initialBalance() != null ? inst.initialBalance().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"D").append(row).append("\"><v>").append(inst.principalAmortization() != null ? inst.principalAmortization().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"E").append(row).append("\"><v>").append(inst.interest() != null ? inst.interest().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"F").append(row).append("\"><v>").append(inst.lifeInsurance() != null ? inst.lifeInsurance().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"G").append(row).append("\"><v>").append(inst.propertyInsurance() != null ? inst.propertyInsurance().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"H").append(row).append("\"><v>").append(inst.adminFee() != null ? inst.adminFee().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"I").append(row).append("\"><v>").append(inst.totalPayment() != null ? inst.totalPayment().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"J").append(row).append("\"><v>").append(inst.finalBalance() != null ? inst.finalBalance().toPlainString() : "0.00").append("</v></c>\n");
                sb.append("      <c r=\"K").append(row).append("\" t=\"inlineStr\"><is><t>").append(inst.graceType() != null ? inst.graceType().name() : "NONE").append("</t></is></c>\n");
                sb.append("    </row>\n");
                row++;
            }
        }

        sb.append("  </sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private int addSummaryRow(StringBuilder sb, int row, String label, String value) {
        sb.append("    <row r=\"").append(row).append("\">\n");
        sb.append("      <c r=\"A").append(row).append("\" t=\"inlineStr\" s=\"1\"><is><t>").append(escapeXml(label)).append("</t></is></c>\n");
        sb.append("      <c r=\"B").append(row).append("\" t=\"inlineStr\"><is><t>").append(escapeXml(value)).append("</t></is></c>\n");
        sb.append("    </row>\n");
        return row + 1;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
