package com.interview.user.interfaces;

import com.interview.user.application.UserAccountService;
import com.interview.user.domain.UserAccount;
import com.interview.user.interfaces.dto.BalanceResponse;
import com.interview.user.interfaces.dto.CreateUserRequest;
import com.interview.user.interfaces.dto.TopUpRequest;
import com.interview.user.interfaces.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        String userId = UUID.randomUUID().toString();
        UserAccount account = userAccountService.createUser(userId, request.getUsername());
        UserResponse response = new UserResponse(
                account.getUserId(),
                account.getUsername(),
                account.getBalance().amount()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String userId) {
        UserAccount account = userAccountService.getUser(userId);
        UserResponse response = new UserResponse(
                account.getUserId(),
                account.getUsername(),
                account.getBalance().amount()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/topup")
    public ResponseEntity<Void> topUp(@PathVariable String userId,
                                      @Valid @RequestBody TopUpRequest request) {
        userAccountService.topUp(userId, request.getAmount());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String userId) {
        java.math.BigDecimal balance = userAccountService.getBalance(userId);
        BalanceResponse response = new BalanceResponse(userId, balance);
        return ResponseEntity.ok(response);
    }
}
