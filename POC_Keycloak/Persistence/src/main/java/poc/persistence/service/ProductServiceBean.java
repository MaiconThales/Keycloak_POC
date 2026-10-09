package poc.persistence.service;

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import poc.persistence.entity.Product;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ProductServiceBean implements ProductServiceLocal {
    @PersistenceContext(unitName = "MinhaAppPU")
    private EntityManager entityManager;

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"})
    public List<Product> findAll() {
        return entityManager.createQuery("SELECT p FROM Product p ORDER BY p.id", Product.class)
                .getResultList();
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "Admin-Write", "Sub-Admin-Write"})
    public Product create(String name, BigDecimal price, String sku) {
        Product product = new Product(name, price, sku);
        entityManager.persist(product);
        return product;
    }
}
