package poc.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

/** Reads Keycloak roles without making assumptions about the configured client. */
final class JwtRoleClaims {
    private JwtRoleClaims() {
    }

    static Set<String> read(JsonObject claims) {
        Set<String> roles = new HashSet<>();
        addArray(roles, claims.getJsonArray("roles"));
        addArray(roles, claims.getJsonArray("groups"));
        addAccessRoles(roles, claims.getJsonObject("realm_access"));

        JsonObject resources = claims.getJsonObject("resource_access");
        if (resources != null) {
            for (String client : resources.keySet()) {
                addAccessRoles(roles, resources.getJsonObject(client));
            }
        }
        addEquivalentPermissions(roles);
        return Collections.unmodifiableSet(roles);
    }

    static boolean containsPermission(Set<String> claims, String permission) {
        return claims.contains(permission);
    }

    private static void addAccessRoles(Set<String> roles, JsonObject access) {
        if (access != null) {
            addArray(roles, access.getJsonArray("roles"));
        }
    }

    private static void addArray(Set<String> roles, JsonArray values) {
        if (values == null) {
            return;
        }
        for (JsonValue value : values) {
            if (value.getValueType() == JsonValue.ValueType.STRING) {
                String role = ((javax.json.JsonString) value).getString().trim();
                if (!role.isEmpty()) {
                    roles.add(role);
                }
            }
        }
    }

    /**
     * The contract defines Admin, Sub-Admin and User as equivalent groups for
     * all CRUD permissions of the same scope.  Expand only these exact group
     * names (including Keycloak's usual /group path notation); never infer a
     * group from a granular role.  This keeps the expansion explicit and
     * prevents a similarly named, unrelated group from gaining access.
     */
    private static void addEquivalentPermissions(Set<String> roles) {
        Set<String> permissions = new HashSet<>();
        for (String role : roles) {
            String group = role;
            int slash = group.lastIndexOf('/');
            if (slash >= 0) {
                group = group.substring(slash + 1);
            }
            if ("Admin".equals(group) || "Sub-Admin".equals(group) || "User".equals(group)) {
                // Also expose the canonical base role to the container.  A
                // path-form group (/Admin) must satisfy @RolesAllowed("Admin")
                // just as the equivalent granular permissions do.
                permissions.add(group);
                for (String action : new String[] {"Read", "Write", "Update", "Delete"}) {
                    permissions.add(group + "-" + action);
                }
            }
        }
        roles.addAll(permissions);
    }

}
