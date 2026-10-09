package poc.rest.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PendingRegistrationTicketServiceTest {
    @AfterEach
    void clearTtl() { System.clearProperty("registration.ticket.ttl.seconds"); }

    @Test
    void ticketIsBoundToIdentityAndCannotBeReplayed() {
        PendingRegistrationTicketService service = new PendingRegistrationTicketService();
        String ticket = service.issue("alice");
        assertTrue(service.belongsTo(ticket, "alice"));
        assertFalse(service.belongsTo(ticket, "bob"));
        assertEquals("alice", service.consume(ticket));
        assertNull(service.consume(ticket));
    }

    @Test
    void ticketExpires() throws Exception {
        System.setProperty("registration.ticket.ttl.seconds", "1");
        PendingRegistrationTicketService service = new PendingRegistrationTicketService();
        service.configure();
        java.lang.reflect.Field lifetime = PendingRegistrationTicketService.class.getDeclaredField("lifetimeMillis");
        lifetime.setAccessible(true);
        lifetime.setLong(service, 1L);
        String ticket = service.issue("alice");
        Thread.sleep(5L);
        assertNull(service.consume(ticket));
    }
}
