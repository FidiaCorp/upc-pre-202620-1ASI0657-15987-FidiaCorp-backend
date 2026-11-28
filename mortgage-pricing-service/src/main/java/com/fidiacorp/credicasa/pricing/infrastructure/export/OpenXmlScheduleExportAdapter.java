package com.fidiacorp.credicasa.pricing.infrastructure.export;

import com.fidiacorp.credicasa.pricing.domain.model.FinancialMetrics;
import com.fidiacorp.credicasa.pricing.domain.model.Installment;
import com.fidiacorp.credicasa.pricing.domain.model.Quotation;
import com.fidiacorp.credicasa.pricing.domain.ports.out.ScheduleExportPort;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Adaptador de salida (US12): genera un libro OpenXML SpreadsheetML (.xlsx) a partir del agregado {@link Quotation},
 * sin dependencias externas. El archivo es un ZIP válido (firma {@code PK\x03\x04}).
 */
@Component
public class OpenXmlScheduleExportAdapter implements ScheduleExportPort {
    public static final String MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DEFAULT_TITLE = "CREDICASA - CRONOGRAMA DE PAGOS";
    private static final String[] HEADERS = {"Nro Cuota", "Fecha Vencimiento", "Saldo Inicial", "Amortizacion", "Interes",
            "Seguro Desgravamen", "Seguro Inmueble", "Gastos Admin", "Cuota Total", "Saldo Final", "Periodo Gracia"};

    @Override public String mediaType() { return MEDIA_TYPE; }
    @Override public String fileExtension() { return "xlsx"; }

    @Override
    public byte[] export(Quotation quotation, String title) {
        if (quotation == null) throw new IllegalArgumentException("La cotización es obligatoria para exportar el cronograma.");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", contentTypes());
            entry(zip, "_rels/.rels", rootRelationships());
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
            entry(zip, "xl/workbook.xml", workbook());
            entry(zip, "xl/styles.xml", styles());
            entry(zip, "xl/worksheets/sheet1.xml", sheet(quotation, title == null || title.isBlank() ? DEFAULT_TITLE : title));
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar archivo Excel del cronograma", e);
        }
    }

    private void entry(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbookRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Cronograma\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                + "</styleSheet>";
    }

    private String sheet(Quotation q, String title) {
        StringBuilder sb = new StringBuilder(4096 + q.getSchedule().size() * 900);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int row = 1;
        sb.append("<row r=\"1\">").append(text("A", row, title, true)).append("</row>");
        row++;
        row = summary(sb, row, "ID Cotizacion", String.valueOf(q.getId()));
        row = summary(sb, row, "Moneda", q.getCurrency().name());
        row = summary(sb, row, "Monto del Prestamo", plain(q.getLoanAmount()));
        row = summary(sb, row, "Valor del Inmueble", plain(q.getPropertyValue()));
        row = summary(sb, row, "Plazo (meses)", String.valueOf(q.getTermMonths()));
        FinancialMetrics m = q.getMetrics();
        if (m != null) {
            row = summary(sb, row, "TCEA", plain(m.getTcea()));
            row = summary(sb, row, "TEA", plain(m.getTea()));
            row = summary(sb, row, "VAN", plain(m.getVan()));
            row = summary(sb, row, "TIR Mensual", plain(m.getMonthlyTir()));
        }
        row++;
        sb.append("<row r=\"").append(row).append("\">");
        for (int i = 0; i < HEADERS.length; i++) sb.append(text(column(i), row, HEADERS[i], true));
        sb.append("</row>");
        row++;
        for (Installment inst : q.getSchedule()) {
            sb.append("<row r=\"").append(row).append("\">")
              .append(number("A", row, String.valueOf(inst.getInstallmentNumber())))
              .append(text("B", row, inst.getDueDate() == null ? "" : inst.getDueDate().toString(), false))
              .append(number("C", row, plain(inst.getInitialBalance())))
              .append(number("D", row, plain(inst.getPrincipalAmortization())))
              .append(number("E", row, plain(inst.getInterest())))
              .append(number("F", row, plain(inst.getLifeInsurance())))
              .append(number("G", row, plain(inst.getPropertyInsurance())))
              .append(number("H", row, plain(inst.getAdminFee())))
              .append(number("I", row, plain(inst.getTotalPayment())))
              .append(number("J", row, plain(inst.getFinalBalance())))
              .append(text("K", row, inst.getGraceType().name(), false))
              .append("</row>");
            row++;
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private int summary(StringBuilder sb, int row, String label, String value) {
        sb.append("<row r=\"").append(row).append("\">").append(text("A", row, label, true)).append(text("B", row, value, false)).append("</row>");
        return row + 1;
    }

    private static String column(int index) { return String.valueOf((char) ('A' + index)); }

    private static String plain(BigDecimal value) { return value == null ? "0.00" : value.toPlainString(); }

    private static String text(String col, int row, String value, boolean bold) {
        return "<c r=\"" + col + row + "\" t=\"inlineStr\"" + (bold ? " s=\"1\"" : "") + "><is><t>" + escape(value) + "</t></is></c>";
    }

    private static String number(String col, int row, String value) {
        return "<c r=\"" + col + row + "\"><v>" + value + "</v></c>";
    }

    static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
