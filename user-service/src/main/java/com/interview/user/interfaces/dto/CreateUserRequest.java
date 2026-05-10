package com.interview.user.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {

    @NotBlank
    private String username;

    public CreateUserRequest() {}

    public CreateUserRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
