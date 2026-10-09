package poc.rest.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;

/** Issues opaque, short-lived and single-use tickets for the pending flow. */
@Singleton
@LocalBean
public class PendingRegistrationTicketService {
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Ticket> tickets = new ConcurrentHashMap<String, Ticket>();
    private volatile long lifetimeMillis = TimeUnit.MINUTES.toMillis(5);

    @PostConstruct
    void configure() {
        String configured = System.getProperty("registration.ticket.ttl.seconds", "300");
        try {
            long seconds = Long.parseLong(configured);
            if (seconds > 0 && seconds <= 3600) lifetimeMillis = TimeUnit.SECONDS.toMillis(seconds);
        } catch (NumberFormatException ignored) { }
    }

    public String issue(String username) {
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Username is required.");
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(username, System.currentTimeMillis() + lifetimeMillis));
        return token;
    }

    /** Atomically consumes a valid ticket and returns its server-derived identity. */
    public String consume(String token) {
        if (token == null || token.length() < 40 || token.length() > 128) return null;
        Ticket ticket = tickets.remove(token);
        if (ticket == null || ticket.expiresAt < System.currentTimeMillis()) return null;
        return ticket.username;
    }

    /** Used before consuming so a mismatched username cannot burn a valid ticket. */
    public boolean belongsTo(String token, String username) {
        Ticket ticket = token == null ? null : tickets.get(token);
        return ticket != null && ticket.expiresAt >= System.currentTimeMillis()
                && constantTimeEquals(ticket.username, username);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return Arrays.equals(left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                right.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static final class Ticket {
        private final String username;
        private final long expiresAt;
        private Ticket(String username, long expiresAt) { this.username = username; this.expiresAt = expiresAt; }
    }
}
