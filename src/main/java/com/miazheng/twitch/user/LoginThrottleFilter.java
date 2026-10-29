package com.miazheng.twitch.user;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;


/**
 * Slows down password guessing on POST /login. After too many failed logins for one username,
 * or from one IP address, further attempts get 429 until no failure has been counted for
 * {@link #WINDOW}. The username limit protects one account from guessing; the address limit
 * stops one client from trying a common password against many accounts.
 * <p>
 * A successful login clears its username's count but not its address's, so an attacker cannot
 * reset the address limit by logging into an account of their own. Counts live in memory: they
 * reset on restart and are not shared between instances.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {


    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);


    private final Cache<String, Integer> failuresByUsername = newCounter();
    private final Cache<String, Integer> failuresByAddress = newCounter();


    // Bounded, so a flood of made-up usernames cannot use up memory
    private static Cache<String, Integer> newCounter() {
        return Caffeine.newBuilder().expireAfterWrite(WINDOW).maximumSize(100_000).build();
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath()));
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // MySQL compares usernames case-insensitively, so "Alice" and "alice" are one account
        String username = Objects.toString(request.getParameter("username"), "").trim().toLowerCase(Locale.ROOT);
        String address = request.getRemoteAddr();


        Duration waitForUsername = waitFor(failuresByUsername, username, MAX_FAILURES_PER_USERNAME);
        Duration waitForAddress = waitFor(failuresByAddress, address, MAX_FAILURES_PER_ADDRESS);
        Duration wait = waitForUsername.compareTo(waitForAddress) >= 0 ? waitForUsername : waitForAddress;
        if (!wait.isZero()) {
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(wait.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many failed logins. Try again in " + minutes
                    + " minute" + (minutes == 1 ? "" : "s") + ".\",\"error\":\"too_many_login_attempts\"}");
            return;
        }


        chain.doFilter(request, response);


        if (response.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            failuresByUsername.asMap().merge(username, 1, Integer::sum);
            failuresByAddress.asMap().merge(address, 1, Integer::sum);
        } else if (response.getStatus() == HttpStatus.NO_CONTENT.value()) {
            failuresByUsername.invalidate(username);
        }
    }


    // How long until the key may try again; zero if it is under the limit
    private static Duration waitFor(Cache<String, Integer> failures, String key, int limit) {
        Integer count = failures.getIfPresent(key);
        if (count == null || count < limit) {
            return Duration.ZERO;
        }
        Duration age = failures.policy().expireAfterWrite().flatMap(policy -> policy.ageOf(key)).orElse(Duration.ZERO);
        Duration left = WINDOW.minus(age);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
