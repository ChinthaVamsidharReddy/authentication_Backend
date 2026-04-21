package com.authentication.dto.request;


import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    @NotBlank
    private String refreshToken;
}