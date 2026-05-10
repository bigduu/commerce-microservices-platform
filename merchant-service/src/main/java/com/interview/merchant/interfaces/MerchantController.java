package com.interview.merchant.interfaces;

import com.interview.merchant.application.MerchantService;
import com.interview.merchant.domain.MerchantAccount;
import com.interview.merchant.interfaces.dto.BalanceResponse;
import com.interview.merchant.interfaces.dto.CreateMerchantRequest;
import com.interview.merchant.interfaces.dto.MerchantResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public ResponseEntity<MerchantResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        String merchantId = UUID.randomUUID().toString();
        MerchantAccount account = merchantService.createMerchant(merchantId, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping("/{merchantId}")
    public ResponseEntity<MerchantResponse> getMerchant(@PathVariable String merchantId) {
        MerchantAccount account = merchantService.getMerchant(merchantId);
        return ResponseEntity.ok(toResponse(account));
    }

    @GetMapping("/{merchantId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String merchantId) {
        BigDecimal balance = merchantService.getBalance(merchantId);
        return ResponseEntity.ok(new BalanceResponse(merchantId, balance));
    }

    private MerchantResponse toResponse(MerchantAccount account) {
        return new MerchantResponse(
                account.getMerchantId(),
                account.getMerchantName(),
                account.getBalance()
        );
    }
}
