package com.fidiacorp.credicasa.quotation.infrastructure.export;

import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.dto.snapshot.QuotationSnapshot;
import com.fidiacorp.credicasa.quotation.application.ports.ScheduleDocumentRenderer;
import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/**
 * US22: PDF (OpenPDF, Java puro) de la versión: encabezado con oferta, vigencia y SHA-256 del snapshot, indicadores
 * (TCEA, VAN, TIR) y el cronograma completo. Se genera solo desde el snapshot inmutable, igual que el XLSX.
 */
@Component
public class SnapshotPdfRenderer implements ScheduleDocumentRenderer {
    private static final String[] HEADERS = {"N°", "Vencimiento", "Saldo inicial", "Amortización", "Interés", "Seguros", "Gastos",
            "Cuota total", "Saldo final"};

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] render(QuotationSnapshot s, String snapshotHash) {
        OfferScheduleResponse q = s.schedule();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("CrediCasa - Cotización v" + s.versionNumber());
            document.addSubject("Oferta " + s.offerId());
            document.addCreator("CrediCasa quotation-deal-service");
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(17, 54, 99));
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font small = FontFactory.getFont(FontFactory.COURIER, 7);
            document.add(new Paragraph("CrediCasa · " + s.title() + " · Versión " + s.versionNumber(), title));
            document.add(new Paragraph("Oferta DEMO " + s.offerId() + " — " + s.bankName(), normal));
            document.add(new Paragraph("Inmueble: " + s.property().title() + " · " + s.property().address(), normal));
            document.add(new Paragraph("Emitida: " + s.issuedAt() + "   Vigente hasta: " + s.expiresAt(), normal));
            document.add(new Paragraph("SHA-256 del snapshot: " + snapshotHash, small));
            document.add(new Paragraph(" "));
            PdfPTable summary = new PdfPTable(6);
            summary.setWidthPercentage(100);
            cell(summary, "Préstamo (" + s.terms().currency() + ")", bold);
            cell(summary, "TEA", bold);
            cell(summary, "Plazo", bold);
            cell(summary, "TCEA", bold);
            cell(summary, "VAN", bold);
            cell(summary, "Total intereses", bold);
            cell(summary, plain(s.terms().loanAmount()), normal);
            cell(summary, plain(s.terms().annualInterestRate()), normal);
            cell(summary, s.terms().termMonths() + " meses", normal);
            cell(summary, plain(q.metrics().tcea()), normal);
            cell(summary, plain(q.metrics().van()), normal);
            cell(summary, plain(q.metrics().totalInterest()), normal);
            document.add(summary);
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String header : HEADERS) {
                PdfPCell head = new PdfPCell(new Phrase(header, bold));
                head.setBackgroundColor(new Color(226, 234, 244));
                table.addCell(head);
            }
            for (OfferScheduleResponse.Row row : q.schedule()) {
                cell(table, String.valueOf(row.installmentNumber()), normal);
                cell(table, String.valueOf(row.dueDate()), normal);
                cell(table, plain(row.initialBalance()), normal);
                cell(table, plain(row.principalAmortization()), normal);
                cell(table, plain(row.interest()), normal);
                cell(table, plain(add(row.lifeInsurance(), row.propertyInsurance())), normal);
                cell(table, plain(row.adminFee()), normal);
                cell(table, plain(row.totalPayment()), normal);
                cell(table, plain(row.finalBalance()), normal);
            }
            document.add(table);
            document.add(new Paragraph("Documento demostrativo generado desde la versión inmutable; no constituye aprobación bancaria.", small));
        } catch (DocumentException e) {
            throw new IllegalStateException("Error al generar el PDF de la versión", e);
        } finally {
            if (document.isOpen()) document.close();
        }
        return out.toByteArray();
    }

    private static void cell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }
}
