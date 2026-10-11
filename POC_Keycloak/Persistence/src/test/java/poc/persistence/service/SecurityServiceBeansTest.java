package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.junit.jupiter.api.Test;

class SecurityServiceBeansTest {
    @Test
    void reviewBeanIsCmtAndUsesGranularRoles() throws Exception {
        assertNotNull(ReviewServiceBean.class.getAnnotation(Stateless.class));
        assertEquals(TransactionAttributeType.REQUIRED,
                ReviewServiceBean.class.getAnnotation(TransactionAttribute.class).value());
        assertRoles(ReviewServiceBean.class.getMethod("findByProduct", Long.class),
                "Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read");
        assertRoles(ReviewServiceBean.class.getMethod("create", Long.class, String.class, String.class),
                "Admin", "Sub-Admin", "User", "Admin-Write", "Sub-Admin-Write", "User-Write");
        assertRoles(ReviewServiceBean.class.getMethod("update", Long.class, String.class, String.class),
                "Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update");
        assertRoles(ReviewServiceBean.class.getMethod("delete", Long.class, String.class),
                "Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete");
    }

    @Test
    void userBeanUsesAdminRoleForEachCrudOperation() throws Exception {
        assertRoles(UserServiceBean.class.getMethod("findAll"), "Admin", "Admin-Read");
        assertRoles(UserServiceBean.class.getMethod("create", String.class, String.class), "Admin", "Admin-Write");
        assertRoles(UserServiceBean.class.getMethod("update", String.class, String.class, String.class), "Admin", "Admin-Update");
        assertRoles(UserServiceBean.class.getMethod("delete", String.class), "Admin", "Admin-Delete");
    }

    @Test
    void productBeanUsesReadRolesForListing() throws Exception {
        assertRoles(ProductServiceBean.class.getMethod("findAll"),
                "Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read");
    }

    private void assertRoles(Method method, String... expected) {
        assertArrayEquals(expected, method.getAnnotation(RolesAllowed.class).value());
    }
}
