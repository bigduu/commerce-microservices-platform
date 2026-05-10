package com.interview.merchant.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateMerchantRequest {

    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
