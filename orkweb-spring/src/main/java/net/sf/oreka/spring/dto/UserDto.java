package net.sf.oreka.spring.dto;

import net.sf.oreka.persistent.OrkUser;

import java.util.Date;

public record UserDto(
        int id,
        String firstname,
        String lastname,
        String email,
        boolean disabled,
        Date dateCreated
) {
    public static UserDto from(OrkUser user) {
        return new UserDto(
                user.getId(),
                user.getFirstname(),
                user.getLastname(),
                user.getEmail(),
                user.isDisabled(),
                user.getDateCreated()
        );
    }
}
