package com.fidiacorp.credicasa.interfaces.rest;
import com.fidiacorp.credicasa.application.dto.request.CreateCreditOfferRequest;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.application.usecase.CreditOfferService;
import com.fidiacorp.credicasa.domain.service.ExcelScheduleExportEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController @Validated @RequestMapping("/api/v1/offers")
@PreAuthorize("hasAnyRole('CLIENT','REALTOR')")
public class CreditOfferController {
    private final CreditOfferService service;
    private final com.fidiacorp.credicasa.application.usecase.OfferScheduleService schedules;
    private final ExcelScheduleExportEngine excelEngine;

    public CreditOfferController(CreditOfferService service,
                                com.fidiacorp.credicasa.application.usecase.OfferScheduleService schedules,
                                ExcelScheduleExportEngine excelEngine) {
        this.service = service;
        this.schedules = schedules;
        this.excelEngine = excelEngine;
    }

    private UUID id(Principal p) {return UUID.fromString(p.getName());}
    @PostMapping("/{offerId}/schedule")
    public ResponseEntity<SimulationResponseDto> generateSchedule(Principal p,@PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(schedules.generate(id(p),offerId));
    }
    @GetMapping("/{offerId}/schedule")
    public ResponseEntity<SimulationResponseDto> getSchedule(Principal p,@PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(schedules.get(id(p),offerId));
    }
    @GetMapping("/{offerId}/schedule/export/xlsx")
    public ResponseEntity<byte[]> exportScheduleExcel(Principal p, @PathVariable UUID offerId) {
        SimulationResponseDto sim = schedules.get(id(p), offerId);
        byte[] bytes = excelEngine.generateExcel(sim, "Cronograma Oferta CrediCasa - " + offerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cronograma_oferta_" + offerId + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
    @PostMapping @PreAuthorize("hasRole('REALTOR')")
    public ResponseEntity<CreditOfferResponse> issue(Principal p,@Valid @RequestBody CreateCreditOfferRequest request) {
        CreditOfferResponse response=service.issue(id(p),request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/offers/"+response.id())).body(response);
    }
    @GetMapping public ResponseEntity<CreditOfferPageResponse> list(Principal p,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listReceived(id(p),page,size));
    }
    @GetMapping("/{offerId}") public ResponseEntity<CreditOfferResponse> get(Principal p,@PathVariable UUID offerId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getReceived(id(p),offerId));
    }
}
