package cn.bafuka.hotarmor.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HotArmor 示例应用启动类
 */
@SpringBootApplication
@MapperScan("cn.bafuka.hotarmor.example.mapper")
public class HotArmorExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotArmorExampleApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  HotArmor Example Application Started!");
        System.out.println("  Sentinel dashboard: http://localhost:8081");
        System.out.println("========================================\n");
    }
}
