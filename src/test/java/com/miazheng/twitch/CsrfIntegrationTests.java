package com.miazheng.twitch;


import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;


// What another site's page can send: the browser attaches our cookies, but the page cannot read
// them, so it cannot copy the CSRF token into a header
public class CsrfIntegrationTests extends IntegrationTest {


    @Test
    public void stateChangingRequests_withoutCsrfToken_shouldBeForbidden() {
        String session = registerAndLogin();


        assertThat(withoutCsrf(HttpMethod.POST, "/favorite", session, favoriteVideoBody(twitch.addVideo("1001"), "1001")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf(HttpMethod.DELETE, "/favorite", session, favoriteVideoBody("v1001-1", "1001")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf(HttpMethod.POST, "/logout", session, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getFavorites(session).videos()).isEmpty();
    }


    @Test
    public void loginAndRegister_withoutCsrfToken_shouldBeForbidden() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", newUsername());
        form.add("password", "pass1234");


        assertThat(rest.postForEntity("/login", form, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withoutCsrf(HttpMethod.POST, "/register", null, Map.of("username", newUsername(), "password", "pass1234")))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }


    @Test
    public void sessionCookie_shouldNotBeSentWithCrossSitePosts() {
        String username = newUsername();
        assertThat(register(username, "pass1234")).isEqualTo(HttpStatus.OK);


        ResponseEntity<String> login = login(username, "pass1234");


        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE))
                .filteredOn(cookie -> cookie.startsWith("JSESSIONID="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("HttpOnly", "SameSite=Lax"));
    }


    private HttpStatus withoutCsrf(HttpMethod method, String path, String session, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (session != null) {
            headers.add(HttpHeaders.COOKIE, session);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return HttpStatus.valueOf(rest.exchange(path, method, new HttpEntity<>(body, headers), String.class).getStatusCode().value());
    }
}
