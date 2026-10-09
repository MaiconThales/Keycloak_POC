package poc.persistence.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class KeycloakAdminServiceBeanTest {
    @Test
    void delegatesAdministrativeBoundaryToClient() {
        KeycloakAdminClient client = mock(KeycloakAdminClient.class);
        KeycloakAdminServiceBean bean = new KeycloakAdminServiceBean(client);
        bean.validateUserManagementAccess();
        verify(client).validateManageUsers();
        KeycloakAdminClient.AdminApiResponse response = new KeycloakAdminClient.AdminApiResponse(204, "");
        org.mockito.Mockito.when(client.userManagement("DELETE", "/users/id", null)).thenReturn(response);
        assertEquals(response, bean.userManagement("DELETE", "/users/id", null));
    }
}
