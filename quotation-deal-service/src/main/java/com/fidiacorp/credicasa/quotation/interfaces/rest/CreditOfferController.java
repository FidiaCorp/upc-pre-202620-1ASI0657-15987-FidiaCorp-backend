package com.fidiacorp.credicasa.quotation.interfaces.rest;

import com.fidiacorp.credicasa.quotation.application.dto.request.CreateCreditOfferRequest;
import com.fidiacorp.credicasa.quotation.application.dto.request.CreateQuotationVersionRequest;
import com.fidiacorp.credicasa.quotation.application.dto.response.CreditOfferPageResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.CreditOfferResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.ExportJobResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.ExportedFile;
import com.fidiacorp.credicasa.quotation.application.dto.response.OfferScheduleResponse;
import com.fidiacorp.credicasa.quotation.application.dto.response.QuotationVersionResponse;
import com.fidiacorp.credicasa.quotation.application.usecase.CreditOfferService;
import com.fidiacorp.credicasa.quotation.application.usecase.OfferScheduleService;
import com.fidiacorp.credicasa.quotation.application.usecase.QuotationVersionService;
import com.fidiacorp.credicasa.quotation.application.usecase.ScheduleExportService;
import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class CreditOfferController {
    private final CreditOfferService offers;
    private final OfferScheduleService schedules;
    private final QuotationVersionService versions;
    private final ScheduleExportService exports;

    public CreditOfferController(CreditOfferService offers, OfferScheduleService schedules, QuotationVersionService versions,
                                 ScheduleExportService exports) {
        this.offers = offers;
        this.schedules = schedules;
        this.versions = versions;
        this.exports = exports;
    }

    private static UUID id(Principal p) {
        return UUID.fromString(p.getName());
    }

    @PostMapping("/offers")
    @PreAuthorize("hasRole('REALTOR')")
    public ResponseEntity<CreditOfferResponse> issue(Principal p, @Valid @RequestBody CreateCreditOfferRequest request) {
        CreditOfferResponse response = offers.issue(id(p), request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/offers/" + response.id())).body(response);
    }

    @GetMapping("/offers")
    public ResponseEntity<CreditOfferPageResponse> list(Principal p, @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.listReceived(id(p), page, size));
    }

    @GetMapping("/offers/{offerId}")
    public ResponseEntity<CreditOfferResponse> get(Principal p, @PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offers.getReceived(id(p), offerId));
    }

    @PostMapping("/offers/{offerId}/schedule")
    public ResponseEntity<OfferScheduleResponse> generateSchedule(Principal p, @PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(schedules.generate(id(p), offerId));
    }

    @GetMapping("/offers/{offerId}/schedule")
    public ResponseEntity<OfferScheduleResponse> getSchedule(Principal p, @PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(schedules.get(id(p), offerId));
    }

    @GetMapping("/offers/{offerId}/schedule/export/xlsx")
    public ResponseEntity<byte[]> exportXlsx(Principal p, @PathVariable UUID offerId, @RequestParam(required = false) @Min(1) Integer version) {
        return download(exports.export(id(p), offerId, version, ExportFormat.XLSX));
    }

    @GetMapping("/offers/{offerId}/schedule/export/pdf")
    public ResponseEntity<byte[]> exportPdf(Principal p, @PathVariable UUID offerId, @RequestParam(required = false) @Min(1) Integer version) {
        return download(exports.export(id(p), offerId, version, ExportFormat.PDF));
    }

    @GetMapping("/offers/{offerId}/exports")
    public ResponseEntity<List<ExportJobResponse>> exportJobs(Principal p, @PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(exports.jobs(id(p), offerId));
    }

    @PostMapping("/offers/{offerId}/versions")
    @PreAuthorize("hasRole('REALTOR')")
    public ResponseEntity<QuotationVersionResponse> createVersion(Principal p, @PathVariable UUID offerId,
                                                                  @Valid @RequestBody(required = false) CreateQuotationVersionRequest request) {
        QuotationVersionResponse created = versions.issueNewVersion(id(p), offerId,
                request == null ? new CreateQuotationVersionRequest(null, null, null, null) : request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(created);
    }

    @GetMapping("/offers/{offerId}/versions")
    public ResponseEntity<List<QuotationVersionResponse>> listVersions(Principal p, @PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(versions.list(id(p), offerId));
    }

    @GetMapping("/offers/{offerId}/versions/{versionNumber}")
    public ResponseEntity<QuotationVersionResponse> getVersion(Principal p, @PathVariable UUID offerId, @PathVariable @Min(1) int versionNumber) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(versions.get(id(p), offerId, versionNumber));
    }

    @GetMapping("/quotations/versions/{versionId}/verify")
    public ResponseEntity<QuotationVersionResponse> verify(Principal p, @PathVariable UUID versionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(versions.verify(id(p), versionId));
    }

    static ResponseEntity<byte[]> download(ExportedFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Snapshot-Sha256", file.snapshotHash())
                .header("X-Content-Sha256", file.contentSha256())
                .header("X-Quotation-Version", Integer.toString(file.versionNumber()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .contentLength(file.content().length)
                .body(file.content());
    }
}
