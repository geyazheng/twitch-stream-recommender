package com.miazheng.twitch.user;


import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.assertj.core.api.Assertions.assertThat;


public class LoginThrottleFilterTests {


    private static final String CORRECT_PASSWORD = "right";


    private final LoginThrottleFilter filter = new LoginThrottleFilter();


    @Test
    public void whenUsernameFailsTooOften_login_shouldBeRefusedEvenWithTheRightPassword() throws Exception {
        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_USERNAME; i++) {
            assertThat(login("alice", "wrong", "10.0.0.1").getStatus()).isEqualTo(401);
        }


        MockHttpServletResponse refused = login("alice", CORRECT_PASSWORD, "10.0.0.2");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, LoginThrottleFilter.WINDOW.toSeconds());
        assertThat(refused.getContentAsString()).contains("too_many_login_attempts", "Try again in 15 minutes");
        // Usernames differing only in case are the same account
        assertThat(login("ALICE", CORRECT_PASSWORD, "10.0.0.3").getStatus()).isEqualTo(429);
        assertThat(login("bob", CORRECT_PASSWORD, "10.0.0.1").getStatus()).isEqualTo(204);
    }


    @Test
    public void whenLoginSucceeds_usernameFailures_shouldStartOver() throws Exception {
        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_USERNAME - 1; i++) {
            login("carol", "wrong", "10.0.1.1");
        }
        assertThat(login("carol", CORRECT_PASSWORD, "10.0.1.1").getStatus()).isEqualTo(204);


        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_USERNAME - 1; i++) {
            login("carol", "wrong", "10.0.1.1");
        }
        assertThat(login("carol", CORRECT_PASSWORD, "10.0.1.1").getStatus()).isEqualTo(204);
    }


    @Test
    public void whenAddressFailsTooOften_login_shouldBeRefusedForEveryUsername() throws Exception {
        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_ADDRESS; i++) {
            login("user" + i, "wrong", "10.0.2.1");
        }


        assertThat(login("someone-else", CORRECT_PASSWORD, "10.0.2.1").getStatus()).isEqualTo(429);
        assertThat(login("someone-else", CORRECT_PASSWORD, "10.0.2.2").getStatus()).isEqualTo(204);
    }


    @Test
    public void otherRequests_shouldPassThroughUntouched() throws Exception {
        for (int i = 0; i < LoginThrottleFilter.MAX_FAILURES_PER_USERNAME; i++) {
            login("dave", "wrong", "10.0.3.1");
        }


        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favorite");
        request.setServletPath("/favorite");
        request.setParameter("username", "dave");
        request.setParameter("password", CORRECT_PASSWORD);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new FakeLoginServlet()));


        assertThat(response.getStatus()).isEqualTo(204);
    }


    private MockHttpServletResponse login(String username, String password, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setServletPath("/login");
        request.setParameter("username", username);
        request.setParameter("password", password);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new FakeLoginServlet()));
        return response;
    }


    // Stands in for Spring Security's form login: 204 for the right password, 401 otherwise
    private static class FakeLoginServlet extends HttpServlet {
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) {
            response.setStatus(CORRECT_PASSWORD.equals(request.getParameter("password")) ? 204 : 401);
        }
    }
}
