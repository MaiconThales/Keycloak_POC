package poc.persistence.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * A comment made by a Keycloak user for a product.
 *
 * <p>The association is intentionally unidirectional: a review knows its
 * product, while {@link Product} does not keep a collection of reviews. The
 * identity value is kept as a scalar because users are managed by Keycloak,
 * not by a local JPA entity.</p>
 */
@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_keycloak_id", nullable = false, length = 255)
    private String userKeycloakId;

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    private String comment;

    public Review() {
    }

    public Review(Product product, String userKeycloakId, String comment) {
        this.product = product;
        this.userKeycloakId = userKeycloakId;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getUserKeycloakId() {
        return userKeycloakId;
    }

    public void setUserKeycloakId(String userKeycloakId) {
        this.userKeycloakId = userKeycloakId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
