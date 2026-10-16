package net.sf.oreka.spring.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String loginString,
        @NotBlank String password,
        @NotBlank String captchaId,
        @NotBlank String captchaCode
) {
}
