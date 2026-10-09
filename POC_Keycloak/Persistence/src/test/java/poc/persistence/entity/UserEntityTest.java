package poc.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserEntityTest {
    @Test
    void supportsJpaNoArgumentConstructionAndAccessors() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setKeycloakId("kc-7");
        user.setEmail("user@example.test");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setActive(Boolean.FALSE);

        assertEquals(Long.valueOf(7L), user.getId());
        assertEquals("kc-7", user.getKeycloakId());
        assertEquals("user@example.test", user.getEmail());
        assertEquals("Ada", user.getFirstName());
        assertEquals("Lovelace", user.getLastName());
        assertFalse(user.getActive());
    }

    @Test
    void constructsUserWithoutAssigningAnIdentifier() {
        UserEntity user = new UserEntity("kc-8", "grace@example.test",
                "Grace", "Hopper", Boolean.TRUE);

        assertNull(user.getId());
        assertEquals("kc-8", user.getKeycloakId());
        assertEquals("grace@example.test", user.getEmail());
        assertEquals("Grace", user.getFirstName());
        assertEquals("Hopper", user.getLastName());
        assertEquals(Boolean.TRUE, user.getActive());
    }
}
