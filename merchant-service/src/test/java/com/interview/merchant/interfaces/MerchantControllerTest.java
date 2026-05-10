package com.interview.merchant.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.merchant.application.MerchantService;
import com.interview.merchant.domain.MerchantAccount;
import com.interview.merchant.interfaces.dto.CreateMerchantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private MerchantService merchantService;

    @InjectMocks
    private MerchantController merchantController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(merchantController).build();
    }

    @Test
    void createMerchantReturns201() throws Exception {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName("Test Merchant");

        MerchantAccount account = new MerchantAccount("merchant-123", "Test Merchant");
        when(merchantService.createMerchant(any(), eq("Test Merchant"))).thenReturn(account);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").value("merchant-123"))
                .andExpect(jsonPath("$.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getMerchantReturns200() throws Exception {
        MerchantAccount account = new MerchantAccount("merchant-123", "Test Merchant");
        when(merchantService.getMerchant("merchant-123")).thenReturn(account);

        mockMvc.perform(get("/api/v1/merchants/merchant-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value("merchant-123"))
                .andExpect(jsonPath("$.merchantName").value("Test Merchant"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getBalanceReturns200() throws Exception {
        when(merchantService.getBalance("merchant-123")).thenReturn(new BigDecimal("100.50"));

        mockMvc.perform(get("/api/v1/merchants/merchant-123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value("merchant-123"))
                .andExpect(jsonPath("$.balance").value(100.50));
    }
}
