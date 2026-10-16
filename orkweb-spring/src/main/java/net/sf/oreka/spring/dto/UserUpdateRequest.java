package net.sf.oreka.spring.dto;

public record UserUpdateRequest(
        String firstname,
        String lastname,
        String email,
        String password,
        Boolean disabled
) {
}
