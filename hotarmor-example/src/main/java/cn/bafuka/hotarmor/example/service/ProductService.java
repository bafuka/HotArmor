package cn.bafuka.hotarmor.example.service;

import cn.bafuka.hotarmor.annotation.HotArmorCache;
import cn.bafuka.hotarmor.annotation.HotArmorEvict;
import cn.bafuka.hotarmor.example.entity.Product;
import cn.bafuka.hotarmor.example.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品服务
 * 演示 HotArmor 对高并发场景的支持
 */
@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    /**
     * 根据ID查询商品（热点商品会被晋升到L1缓存）
     *
     * @param productId 商品ID
     * @return 商品信息
     */
    @HotArmorCache(resource = "product:detail", key = "#productId")
    public Product getProductById(Long productId) {
        log.info("从数据库查询商品: productId={}", productId);
        // 模拟数据库查询延迟
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return productMapper.selectById(productId);
    }

    /**
     * 查询所有商品
     *
     * @return 商品列表
     */
    public List<Product> getAllProducts() {
        return productMapper.selectList(null);
    }

    /**
     * 创建商品
     *
     * @param product 商品信息
     * @return 创建的商品
     */
    public Product createProduct(Product product) {
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);
        log.info("商品创建成功: productId={}", product.getId());
        return product;
    }

    /**
     * 更新商品（自动失效缓存）
     *
     * @param product 商品信息
     */
    @HotArmorEvict(resource = "product:detail", key = "#product.id")
    public void updateProduct(Product product) {
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        log.info("商品更新成功: productId={}", product.getId());
    }

    /**
     * 删除商品
     *
     * @param productId 商品ID
     */
    @HotArmorEvict(resource = "product:detail", key = "#productId")
    public void deleteProduct(Long productId) {
        productMapper.deleteById(productId);
        log.info("商品删除成功: productId={}", productId);
    }
}
