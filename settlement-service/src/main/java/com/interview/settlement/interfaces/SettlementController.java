package com.interview.settlement.interfaces;

import com.interview.settlement.application.SettlementService;
import com.interview.settlement.domain.SettlementReport;
import com.interview.settlement.interfaces.dto.SettlementResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getSettlements(@PathVariable String merchantId) {
        List<SettlementReport> reports = settlementService.getSettlements(merchantId);
        List<SettlementResponse> responses = reports.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/latest")
    public ResponseEntity<SettlementResponse> getLatestSettlement(@PathVariable String merchantId) {
        SettlementReport report = settlementService.getLatestSettlement(merchantId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(report));
    }

    private SettlementResponse toResponse(SettlementReport report) {
        return new SettlementResponse(
                report.getReportId(),
                report.getMerchantId(),
                report.getSettlementDate(),
                report.getExpectedRevenue(),
                report.getActualRevenue(),
                report.getStatus().name(),
                report.getOrderCount(),
                report.getCreatedAt()
        );
    }
}
