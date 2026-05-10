package com.interview.user.interfaces;

import com.interview.common.domain.Money;
import com.interview.user.application.UserAccountService;
import com.interview.user.domain.UserAccount;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserAccountService userAccountService;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    @Test
    void createUserShouldReturn201AndUserResponse() throws Exception {
        String userId = "user-1";
        String username = "alice";
        UserAccount account = UserAccount.create(userId, username);

        when(userAccountService.createUser(any(), eq(username))).thenReturn(account);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getUserShouldReturn200AndUserResponse() throws Exception {
        String userId = "user-1";
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(100.00));

        when(userAccountService.getUser(userId)).thenReturn(account);

        mockMvc.perform(get("/api/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void topUpShouldReturn200() throws Exception {
        String userId = "user-1";
        BigDecimal amount = new BigDecimal("50.00");

        mockMvc.perform(post("/api/v1/users/{userId}/topup", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50.00}"))
                .andExpect(status().isOk());
    }

    @Test
    void getBalanceShouldReturn200AndBalanceResponse() throws Exception {
        String userId = "user-1";
        BigDecimal balance = new BigDecimal("150.00");

        when(userAccountService.getBalance(userId)).thenReturn(balance);

        mockMvc.perform(get("/api/v1/users/{userId}/balance", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.balance").value(150.00));
    }
}
