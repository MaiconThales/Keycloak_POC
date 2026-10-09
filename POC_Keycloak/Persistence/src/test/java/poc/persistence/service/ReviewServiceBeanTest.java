package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.Arrays;

import javax.ejb.SessionContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;

class ReviewServiceBeanTest {
    private EntityManager em;
    private SessionContext context;
    private ReviewServiceBean bean;
    private Product product;

    @BeforeEach void setUp() throws Exception {
        em = mock(EntityManager.class); context = mock(SessionContext.class);
        when(context.getCallerPrincipal()).thenReturn((Principal) () -> "trusted-user");
        bean = new ReviewServiceBean(); product = new Product("P", null, "P");
        set("entityManager", em); set("sessionContext", context);
    }
    private void set(String name, Object value) throws Exception {
        Field f = ReviewServiceBean.class.getDeclaredField(name); f.setAccessible(true); f.set(bean, value);
    }
    @SuppressWarnings("unchecked") private TypedQuery<Review> query() {
        TypedQuery<Review> q = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Review.class))).thenReturn(q); return q;
    }

    @Test void findsReviewsAndBindsProduct() {
        TypedQuery<Review> q = query(); Review r = new Review(product, "trusted-user", "ok");
        when(q.getResultList()).thenReturn(Arrays.asList(r));
        assertEquals(1, bean.findByProduct(1L).size());
        verify(q).setParameter("productId", 1L); verify(q).getResultList();
    }
    @Test void rejectsInvalidIdsAndComments() {
        assertThrows(IllegalArgumentException.class, () -> bean.findByProduct(0L));
        assertThrows(IllegalArgumentException.class, () -> bean.create(1L, " "));
        assertThrows(IllegalArgumentException.class, () -> bean.update(null, "x"));
    }
    @Test void createsWithAuthenticatedIdentityNotSuppliedIdentity() {
        when(em.find(Product.class, 1L)).thenReturn(product);
        Review r = bean.create(1L, "forged-user", " hello ");
        assertEquals("trusted-user", r.getUserKeycloakId()); assertEquals("hello", r.getComment());
        verify(em).persist(r);
    }
    @Test void requiresAuthenticatedCaller() {
        when(em.find(Product.class, 1L)).thenReturn(product);
        when(context.getCallerPrincipal()).thenReturn(null);
        assertThrows(SecurityException.class, () -> bean.create(1L, "x"));
    }
    @Test void updatesAndDeletesOnlyOwnedReview() {
        Review r = new Review(product, "trusted-user", "old"); when(em.find(Review.class, 2L)).thenReturn(r);
        assertSame(r, bean.update(2L, "new")); assertEquals("new", r.getComment());
        bean.delete(2L); verify(em).remove(r);
    }
    @Test void ignoresForgedIdentityOnUpdateAndDelete() {
        Review r = new Review(product, "someone-else", "old"); when(em.find(Review.class, 2L)).thenReturn(r);
        assertThrows(SecurityException.class, () -> bean.update(2L, "someone-else", "new"));
        assertThrows(SecurityException.class, () -> bean.delete(2L, "someone-else"));
    }
    @Test void adminAndBothSubAdminSpellingsMayModerate() {
        Review r = new Review(product, "other", "old"); when(em.find(Review.class, 2L)).thenReturn(r);
        when(context.isCallerInRole("Sub-Admin-Update")).thenReturn(true);
        bean.update(2L, "new");
        when(context.isCallerInRole("Sub-Admin-Update")).thenReturn(false);
        when(context.isCallerInRole("Admin-Delete")).thenReturn(true);
        bean.delete(2L); verify(em).remove(r);
    }
    @Test void deleteRoleCannotAuthorizeUpdate() {
        Review r = new Review(product, "other", "old"); when(em.find(Review.class, 2L)).thenReturn(r);
        when(context.isCallerInRole("Admin-Delete")).thenReturn(true);

        assertThrows(SecurityException.class, () -> bean.update(2L, "new"));
        assertEquals("old", r.getComment());
    }
    @Test void updateRoleCannotAuthorizeDelete() {
        Review r = new Review(product, "other", "old"); when(em.find(Review.class, 2L)).thenReturn(r);
        when(context.isCallerInRole("Admin-Update")).thenReturn(true);

        assertThrows(SecurityException.class, () -> bean.delete(2L));
        verify(em, never()).remove(r);
    }
    @Test void failsWhenProductOrReviewDoesNotExist() {
        when(em.find(Product.class, 1L)).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> bean.create(1L, "x"));
        when(em.find(Review.class, 2L)).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> bean.update(2L, "x"));
    }
}
