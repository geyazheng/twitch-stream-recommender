package com.miazheng.twitch;


import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;


public class UserIntegrationTests extends IntegrationTest {


    @Test
    public void register_withTakenUsername_shouldBeConflict() {
        String username = newUsername();
        assertThat(register(username, "pass1234")).isEqualTo(HttpStatus.OK);


        assertThat(register(username, "another-pass")).isEqualTo(HttpStatus.CONFLICT);
        // MySQL compares usernames case-insensitively
        assertThat(register(username.toUpperCase(), "another-pass")).isEqualTo(HttpStatus.CONFLICT);
    }


    @Test
    public void register_withInvalidFields_shouldBeBadRequest() {
        assertThat(register("ab", "pass1234")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("has space", "pass1234")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register(newUsername(), "")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register(newUsername(), "short")).isEqualTo(HttpStatus.BAD_REQUEST);
        // 25 characters but 75 bytes in UTF-8, past the 72 bytes BCrypt can hash
        assertThat(register(newUsername(), "密".repeat(25))).isEqualTo(HttpStatus.BAD_REQUEST);
    }


    // The per-address limit is covered by LoginThrottleFilterTests: every test here comes from
    // 127.0.0.1, so tripping it would lock out the rest of the suite
    @Test
    public void login_afterTooManyWrongPasswords_shouldBeRefused() {
        String username = newUsername();
        assertThat(register(username, "pass1234")).isEqualTo(HttpStatus.OK);
        for (int i = 0; i < 5; i++) {
            assertThat(login(username, "wrong-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }


        ResponseEntity<String> refused = login(username, "pass1234");


        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(refused.getBody()).contains("too_many_login_attempts");
        assertThat(registerAndLogin()).isNotBlank();
    }


    @Test
    public void register_withInvalidField_shouldSayWhatIsWrong() {
        String body = request(HttpMethod.POST, "/register", null, Map.of("username", "ab", "password", "pass1234"), String.class).getBody();


        assertThat(body).contains("invalid_request", "username", "Username must be 3 to 50");
    }
}
