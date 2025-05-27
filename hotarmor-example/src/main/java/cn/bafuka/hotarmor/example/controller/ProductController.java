package cn.bafuka.hotarmor.example.controller;

import cn.bafuka.hotarmor.example.entity.Product;
import cn.bafuka.hotarmor.example.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 根据ID查询商品
     */
    @GetMapping("/{id}")
    public Map<String, Object> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", product);
        return result;
    }

    /**
     * 查询所有商品
     */
    @GetMapping("/list")
    public Map<String, Object> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", products);
        result.put("total", products.size());
        return result;
    }

    /**
     * 创建商品
     */
    @PostMapping
    public Map<String, Object> createProduct(@RequestBody Product product) {
        Product created = productService.createProduct(product);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", created);
        result.put("message", "商品创建成功");
        return result;
    }

    /**
     * 更新商品
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateProduct(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productService.updateProduct(product);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "商品更新成功");
        return result;
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "商品删除成功");
        return result;
    }

    /**
     * 压测接口：批量查询商品（模拟热点访问）
     */
    @GetMapping("/benchmark/{id}")
    public Map<String, Object> benchmark(@PathVariable Long id, @RequestParam(defaultValue = "1") int times) {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < times; i++) {
            productService.getProductById(id);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("times", times);
        result.put("duration", duration + "ms");
        result.put("avg", (duration * 1.0 / times) + "ms");
        return result;
    }
}
