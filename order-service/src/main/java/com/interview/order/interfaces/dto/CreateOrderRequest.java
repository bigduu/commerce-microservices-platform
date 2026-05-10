package com.interview.order.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotBlank String userId,
        @NotBlank String merchantId,
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity
) {
}
