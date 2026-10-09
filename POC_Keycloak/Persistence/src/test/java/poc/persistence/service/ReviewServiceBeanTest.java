package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Arrays;
import java.security.Principal;

import javax.ejb.SessionContext;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;
import poc.persistence.entity.UserEntity;

class ReviewServiceBeanTest {
    private EntityManager entityManager;
    private SessionContext sessionContext;
    private ReviewServiceBean service;
    private Product product;
    private UserEntity author;
    private Review review;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        entityManager = org.mockito.Mockito.mock(EntityManager.class);
        sessionContext = org.mockito.Mockito.mock(SessionContext.class);
        service = new ReviewServiceBean(entityManager, sessionContext);
        product = new Product(); product.setId(10L);
        author = new UserEntity(); author.setId(20L);
        review = new Review(product, author, 4, "good", java.time.LocalDateTime.now());
        review.setId(30L);
    }

    @Test
    void findsWithBothFilters() {
        TypedQuery<Review> query = org.mockito.Mockito.mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(Review.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(review));

        assertEquals(1, service.findReviews(10L, 4).size());
        verify(query).setParameter("productId", 10L);
        verify(query).setParameter("rating", 4);
    }

    @Test
    void createsAndUsesExistingAssociations() {
        when(entityManager.find(Product.class, 10L)).thenReturn(product);
        when(entityManager.find(UserEntity.class, 20L)).thenReturn(author);
        Review result = service.createReview(10L, 20L, 5, "excellent");
        assertEquals(5, result.getRating().intValue());
        verify(entityManager).persist(result);
    }

    @Test
    void rejectsInvalidRatingAndMissingEntities() {
        assertThrows(IllegalArgumentException.class, () -> service.createReview(10L, 20L, 0, "x"));
        when(entityManager.find(Product.class, 10L)).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> service.createReview(10L, 20L, 3, "x"));
    }

    @Test
    void rejectsDuplicateProductAndAuthorBeforePersisting() {
        when(entityManager.find(Product.class, 10L)).thenReturn(product);
        when(entityManager.find(UserEntity.class, 20L)).thenReturn(author);
        TypedQuery<Review> duplicate = org.mockito.Mockito.mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(Review.class))).thenReturn(duplicate);
        when(duplicate.setParameter(anyString(), any())).thenReturn(duplicate);
        when(duplicate.getResultList()).thenReturn(Arrays.asList(review));

        assertThrows(IllegalArgumentException.class, () -> service.createReview(10L, 20L, 5, "again"));
        verify(entityManager, never()).persist(any(Review.class));
    }

    @Test
    void rejectsAuthorIdThatDoesNotBelongToAuthenticatedPrincipal() {
        Principal principal = () -> "john";
        when(sessionContext.getCallerPrincipal()).thenReturn(principal);
        TypedQuery<UserEntity> caller = org.mockito.Mockito.mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(UserEntity.class))).thenReturn(caller);
        when(caller.setParameter(anyString(), any())).thenReturn(caller);
        UserEntity john = new UserEntity(); john.setId(99L);
        when(caller.getResultList()).thenReturn(Arrays.asList(john));
        when(entityManager.find(Product.class, 10L)).thenReturn(product);
        when(entityManager.find(UserEntity.class, 20L)).thenReturn(author);

        assertThrows(SecurityException.class, () -> service.createReview(10L, 20L, 5, "forged"));
        verify(entityManager, never()).persist(any(Review.class));
    }

    @Test
    void ownerMayUpdateAndDelete() {
        when(entityManager.find(Review.class, 30L)).thenReturn(review);
        assertEquals("changed", service.updateReview(30L, 20L, 3, "changed").getComment());
        service.deleteReview(30L, 20L);
        verify(entityManager).remove(review);
    }

    @Test
    void nonOwnerIsRejectedUnlessModerator() {
        when(entityManager.find(Review.class, 30L)).thenReturn(review);
        assertThrows(SecurityException.class, () -> service.updateReview(30L, 99L, 3, "no"));
        when(sessionContext.isCallerInRole("Admin")).thenReturn(true);
        service.deleteReview(30L, 99L);
        verify(entityManager).remove(review);
    }

    @Test
    void validatesUpdateAndDeleteInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.updateReview(null, 1L, 3, "x"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteReview(1L, null));
        verify(entityManager, never()).find(any(), any());
    }

    @Test
    void validatesOptionalFiltersAndMissingReviews() {
        assertThrows(IllegalArgumentException.class, () -> service.findReviews(0L, null));
        assertThrows(IllegalArgumentException.class, () -> service.findReviews(null, 6));
        when(entityManager.find(Review.class, 404L)).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> service.updateReview(404L, 20L, 3, "x"));
        assertThrows(EntityNotFoundException.class, () -> service.deleteReview(404L, 20L));
    }

    @Test
    void moderatorMayUpdateAndAuthorizationContractIsDeclared() throws Exception {
        when(entityManager.find(Review.class, 30L)).thenReturn(review);
        when(sessionContext.isCallerInRole("Admin-Update")).thenReturn(true);
        assertEquals("moderated", service.updateReview(30L, 99L, 5, "moderated").getComment());
        assertNotNull(ReviewServiceBean.class.getAnnotation(Stateless.class));
        assertEquals(TransactionAttributeType.REQUIRED,
                ReviewServiceBean.class.getAnnotation(TransactionAttribute.class).value());
        assertArrayEquals(new String[] {"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"},
                ReviewServiceBean.class.getMethod("updateReview", Long.class, Long.class, Integer.class, String.class)
                        .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[] {"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"},
                ReviewServiceBean.class.getMethod("deleteReview", Long.class, Long.class)
                        .getAnnotation(RolesAllowed.class).value());
    }
}
