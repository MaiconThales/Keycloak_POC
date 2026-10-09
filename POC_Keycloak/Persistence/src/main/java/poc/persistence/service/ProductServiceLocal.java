package poc.persistence.service;

import java.math.BigDecimal;
import java.util.List;

import javax.ejb.Local;

import poc.persistence.entity.Product;

@Local
public interface ProductServiceLocal {
    List<Product> findAll();

    Product create(String name, BigDecimal price, String sku);

    Product updateProduct(Long id, String name, BigDecimal price, String sku);

    void deleteProduct(Long id);
}
