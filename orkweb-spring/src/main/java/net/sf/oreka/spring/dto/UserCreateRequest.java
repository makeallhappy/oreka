package net.sf.oreka.spring.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank String firstname,
        String lastname,
        String email,
        @NotBlank String password,
        String loginString
) {
}
