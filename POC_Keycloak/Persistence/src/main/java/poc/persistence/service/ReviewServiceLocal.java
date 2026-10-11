package poc.persistence.service;

import java.util.List;

import javax.ejb.Local;

import poc.persistence.entity.Review;

/** Transactional use cases for product reviews. */
@Local
public interface ReviewServiceLocal {
    List<Review> findByProduct(Long productId);

    Review create(Long productId, String userKeycloakId, String comment);

    /** Preferred API: ownership is taken from the authenticated caller. */
    Review create(Long productId, String comment);

    Review update(Long id, String userKeycloakId, String comment);
    Review update(Long id, String comment);

    void delete(Long id, String userKeycloakId);
    void delete(Long id);
}
