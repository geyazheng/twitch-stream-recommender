package com.miazheng.twitch.model;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


import java.nio.charset.StandardCharsets;


public record RegisterBody(
        @NotNull(message = "Username is required")
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}", message = "Username must be 3 to 50 letters, digits, '.', '_' or '-'")
        String username,


        @NotNull(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,


        @Size(max = 255) @JsonProperty("first_name") String firstName,
        @Size(max = 255) @JsonProperty("last_name") String lastName
) {


    // BCrypt hashes at most 72 bytes and Spring Security rejects longer passwords. Counted in
    // bytes, not characters: 25 Chinese characters are already 75 bytes in UTF-8.
    @AssertTrue(message = "Password must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
