package com.uws.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyUpiPinRequest {
    @NotBlank(message = "UPI PIN is required")
    @Pattern(regexp = "\\d{6}", message = "UPI PIN must contain exactly 6 digits")
    private String pin;
}
