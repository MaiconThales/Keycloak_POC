package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class UserProfileTest {
    @Test void exposesTheSafeDirectoryProjection() {
        UserProfile profile = new UserProfile("id", "user", "mail@example.test");
        assertEquals("id", profile.getId()); assertEquals("user", profile.getUsername());
        assertEquals("mail@example.test", profile.getEmail());
    }
}
