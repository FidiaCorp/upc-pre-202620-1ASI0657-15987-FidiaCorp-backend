package com.fidiacorp.credicasa.quotation.infrastructure.export;

import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.dto.snapshot.QuotationSnapshot;
import com.fidiacorp.credicasa.quotation.application.ports.ScheduleDocumentRenderer;
import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * US22 (+US12): libro OpenXML (.xlsx) generado solo desde el snapshot inmutable de la versión, sin dependencias
 * externas. Las entradas ZIP usan la fecha de emisión de la versión, por lo que el mismo snapshot produce siempre el
 * mismo archivo (huella SHA-256 reproducible).
 */
@Component
public class SnapshotXlsxRenderer implements ScheduleDocumentRenderer {
    private static final String[] HEADERS = {"Nro Cuota", "Fecha Vencimiento", "Saldo Inicial", "Amortizacion", "Interes",
            "Seguro Desgravamen", "Seguro Inmueble", "Gastos Admin", "Cuota Total", "Saldo Final", "Periodo Gracia"};

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public byte[] render(QuotationSnapshot snapshot, String snapshotHash) {
        long time = snapshot.issuedAt().toEpochMilli();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", contentTypes(), time);
            entry(zip, "_rels/.rels", rootRelationships(), time);
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(), time);
            entry(zip, "xl/workbook.xml", workbook(), time);
            entry(zip, "xl/styles.xml", styles(), time);
            entry(zip, "xl/worksheets/sheet1.xml", sheet(snapshot, snapshotHash), time);
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Error al generar el XLSX de la versión", e);
        }
    }

    private void entry(ZipOutputStream zip, String path, String content, long time) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(time);
        zip.putNextEntry(entry);
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

    private String sheet(QuotationSnapshot s, String hash) {
        OfferScheduleResponse q = s.schedule();
        StringBuilder sb = new StringBuilder(4096 + q.schedule().size() * 900);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int row = 1;
        sb.append("<row r=\"1\">").append(text("A", row, "CREDICASA - COTIZACION " + s.title().toUpperCase(java.util.Locale.ROOT) + " v" + s.versionNumber(), true)).append("</row>");
        row++;
        row = summary(sb, row, "ID Oferta", String.valueOf(s.offerId()));
        row = summary(sb, row, "Version", String.valueOf(s.versionNumber()));
        row = summary(sb, row, "Emitida", s.issuedAt().toString());
        row = summary(sb, row, "Vigente hasta", s.expiresAt().toString());
        row = summary(sb, row, "SHA-256 snapshot", hash);
        row = summary(sb, row, "Inmueble", s.property().title() + " - " + s.property().address());
        row = summary(sb, row, "Moneda", s.terms().currency().name());
        row = summary(sb, row, "Monto del Prestamo", plain(s.terms().loanAmount()));
        row = summary(sb, row, "TEA", plain(s.terms().annualInterestRate()));
        row = summary(sb, row, "Plazo (meses)", String.valueOf(s.terms().termMonths()));
        row = summary(sb, row, "TCEA", plain(q.metrics().tcea()));
        row = summary(sb, row, "VAN", plain(q.metrics().van()));
        row = summary(sb, row, "TIR Mensual", plain(q.metrics().monthlyTir()));
        row = summary(sb, row, "Total Intereses", plain(q.metrics().totalInterest()));
        row++;
        sb.append("<row r=\"").append(row).append("\">");
        for (int i = 0; i < HEADERS.length; i++) sb.append(text(column(i), row, HEADERS[i], true));
        sb.append("</row>");
        row++;
        for (OfferScheduleResponse.Row inst : q.schedule()) {
            sb.append("<row r=\"").append(row).append("\">")
              .append(number("A", row, String.valueOf(inst.installmentNumber())))
              .append(text("B", row, inst.dueDate() == null ? "" : inst.dueDate().toString(), false))
              .append(number("C", row, plain(inst.initialBalance())))
              .append(number("D", row, plain(inst.principalAmortization())))
              .append(number("E", row, plain(inst.interest())))
              .append(number("F", row, plain(inst.lifeInsurance())))
              .append(number("G", row, plain(inst.propertyInsurance())))
              .append(number("H", row, plain(inst.adminFee())))
              .append(number("I", row, plain(inst.totalPayment())))
              .append(number("J", row, plain(inst.finalBalance())))
              .append(text("K", row, inst.graceType(), false))
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
