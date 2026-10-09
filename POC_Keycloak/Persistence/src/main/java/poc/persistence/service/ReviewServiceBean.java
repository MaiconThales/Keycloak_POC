package poc.persistence.service;

import java.util.List;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.security.Principal;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;

/**
 * CMT service for reviews.  REST resources only pass validated input to this
 * bean; ownership and persistence therefore cannot be bypassed by a facade.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ReviewServiceBean implements ReviewServiceLocal {
    @PersistenceContext(unitName = "MinhaAppPU")
    private EntityManager entityManager;

    @Resource
    private SessionContext sessionContext;

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"})
    public List<Review> findByProduct(Long productId) {
        if (productId == null || productId < 1) {
            throw new IllegalArgumentException("Product id must be positive.");
        }
        TypedQuery<Review> query = entityManager.createQuery(
                "SELECT r FROM Review r WHERE r.product.id = :productId ORDER BY r.id",
                Review.class);
        query.setParameter("productId", productId);
        return query.getResultList();
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Write", "Sub-Admin-Write", "User-Write"})
    public Review create(Long productId, String userKeycloakId, String comment) {
        return create(productId, comment);
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Write", "Sub-Admin-Write", "User-Write"})
    public Review create(Long productId, String comment) {
        validateComment(comment);
        Product product = required(Product.class, productId, "Product");
        Review review = new Review(product, callerIdentity(), comment.trim());
        entityManager.persist(review);
        return review;
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"})
    public Review update(Long id, String userKeycloakId, String comment) {
        return update(id, comment);
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"})
    public Review update(Long id, String comment) {
        validateId(id, "Review id");
        validateComment(comment);
        Review review = required(Review.class, id, "Review");
        ensureOwner(review, "Admin-Update", "Sub-Admin-Update");
        review.setComment(comment.trim());
        return review;
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"})
    public void delete(Long id, String userKeycloakId) {
        delete(id);
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"})
    public void delete(Long id) {
        validateId(id, "Review id");
        Review review = required(Review.class, id, "Review");
        ensureOwner(review, "Admin-Delete", "Sub-Admin-Delete");
        entityManager.remove(review);
    }

    private <T> T required(Class<T> type, Long id, String label) {
        validateId(id, label + " id");
        T value = entityManager.find(type, id);
        if (value == null) {
            throw new EntityNotFoundException(label + " not found: " + id);
        }
        return value;
    }

    /**
     * Moderation is action-specific.  In particular, a delete grant must not
     * become an update grant (and vice versa) merely because both operations
     * check ownership in this bean.
     */
    private void ensureOwner(Review review, String... moderationRoles) {
        String owner = review.getUserKeycloakId();
        boolean moderator = sessionContext != null && hasAnyRole(moderationRoles);
        if (!moderator && (owner == null || !owner.equals(callerIdentity()))) {
            throw new SecurityException("Only the review owner may change this review.");
        }
    }

    private boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (sessionContext.isCallerInRole(role)) {
                return true;
            }
        }
        return false;
    }

    /** The bearer-token principal is the only accepted source of ownership. */
    private String callerIdentity() {
        if (sessionContext == null) {
            throw new SecurityException("Authenticated caller is required.");
        }
        Principal principal = sessionContext.getCallerPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()
                || "anonymous".equalsIgnoreCase(principal.getName())) {
            throw new SecurityException("Authenticated caller is required.");
        }
        return principal.getName().trim();
    }

    private void validateId(Long id, String label) {
        if (id == null || id < 1) throw new IllegalArgumentException(label + " must be positive.");
    }

    private void validateComment(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Comment is required.");
        }
    }
}
