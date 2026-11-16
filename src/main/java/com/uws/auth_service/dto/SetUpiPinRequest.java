package com.uws.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetUpiPinRequest {

    @NotBlank(message = "Current account password is required")
    private String currentPassword;

    @NotBlank(message = "UPI PIN is required")
    @Pattern(regexp = "\\d{6}", message = "UPI PIN must contain exactly 6 digits")
    private String newPin;

    @NotBlank(message = "UPI PIN confirmation is required")
    @Pattern(regexp = "\\d{6}", message = "UPI PIN must contain exactly 6 digits")
    private String confirmPin;
}
