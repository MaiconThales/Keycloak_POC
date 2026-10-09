package poc.persistence.service;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

import poc.persistence.entity.Product;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ProductServiceBean implements ProductServiceLocal {
    @PersistenceContext(unitName = "MinhaAppPU")
    private EntityManager entityManager;

    @Override
    @PermitAll
    public List<Product> findAll() {
        return entityManager.createQuery("SELECT p FROM Product p ORDER BY p.id", Product.class)
                .getResultList();
    }

    @Override
    @RolesAllowed({"admin", "Admin", "Sub-Admin", "Admin-Write", "Sub-Admin-Write"})
    public Product create(String name, BigDecimal price, String sku) {
        Product product = new Product(name, price, sku);
        entityManager.persist(product);
        return product;
    }

    /**
     * Updates the managed entity in the current CMT transaction.  Calling
     * {@code find} rather than merging a caller supplied object keeps the
     * operation scoped to the product identified by the API and makes a
     * missing product an explicit business failure.
     */
    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "Admin-Write", "Sub-Admin-Write", "Admin-Update", "Sub-Admin-Update"})
    public Product updateProduct(Long id, String name, BigDecimal price, String sku) {
        validateProduct(id, name, price, sku);
        Product product = findRequired(id);
        product.setName(name);
        product.setPrice(price);
        product.setSku(sku);
        return product;
    }

    @Override
    @RolesAllowed({"Admin", "Admin-Write", "Admin-Delete"})
    public void deleteProduct(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product id is required.");
        }
        entityManager.remove(findRequired(id));
    }

    private Product findRequired(Long id) {
        Product product = entityManager.find(Product.class, id);
        if (product == null) {
            throw new EntityNotFoundException("Product not found: " + id);
        }
        return product;
    }

    private void validateProduct(Long id, String name, BigDecimal price, String sku) {
        if (id == null) {
            throw new IllegalArgumentException("Product id is required.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero.");
        }
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("Product SKU is required.");
        }
    }
}
