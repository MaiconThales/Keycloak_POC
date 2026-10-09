package poc.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.jupiter.api.Test;

class JwtRoleClaimsTest {
    @Test
    void readsTopLevelNestedAndGroupClaims() {
        JsonObject claims = Json.createReader(new StringReader(
                "{\"roles\":[\"User-Read\"],\"groups\":[\"/Admin\"],"
                        + "\"realm_access\":{\"roles\":[\"Admin-Update\"]},"
                        + "\"resource_access\":{\"api\":{\"roles\":[\"Sub-Admin-Delete\"]}}}"))
                .readObject();

        Set<String> roles = JwtRoleClaims.read(claims);

        assertTrue(roles.contains("User-Read"));
        assertTrue(roles.contains("Admin-Update"));
        assertTrue(roles.contains("Sub-Admin-Delete"));
        assertTrue(JwtRoleClaims.containsPermission(roles, "Admin-Update"));
    }

    @Test
    void baseGroupGrantsOnlyItsExplicitEquivalentPermissions() {
        JsonObject claims = Json.createReader(new StringReader(
                "{\"groups\":[\"Sub-Admin\"]}"))
                .readObject();

        Set<String> roles = JwtRoleClaims.read(claims);
        assertTrue(JwtRoleClaims.containsPermission(roles, "Sub-Admin-Write"));
        assertTrue(JwtRoleClaims.containsPermission(roles, "Sub-Admin-Delete"));
        assertFalse(JwtRoleClaims.containsPermission(roles, "Admin-Write"));
        assertFalse(JwtRoleClaims.containsPermission(roles, "User-Write"));
    }
}
