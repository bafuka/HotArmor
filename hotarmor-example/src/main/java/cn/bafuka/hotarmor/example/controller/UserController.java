package cn.bafuka.hotarmor.example.controller;

import cn.bafuka.hotarmor.example.entity.User;
import cn.bafuka.hotarmor.example.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    public Map<String, Object> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", user);
        return result;
    }

    /**
     * 根据用户名查询用户
     */
    @GetMapping("/username/{username}")
    public Map<String, Object> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", user);
        return result;
    }

    /**
     * 查询所有用户
     */
    @GetMapping("/list")
    public Map<String, Object> getAllUsers() {
        List<User> users = userService.getAllUsers();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", users);
        result.put("total", users.size());
        return result;
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Map<String, Object> createUser(@RequestBody User user) {
        User created = userService.createUser(user);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", created);
        result.put("message", "用户创建成功");
        return result;
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.updateUser(user);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户更新成功");
        return result;
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "用户删除成功");
        return result;
    }

    /**
     * 压测接口：批量查询用户（模拟热点访问）
     */
    @GetMapping("/benchmark/{id}")
    public Map<String, Object> benchmark(@PathVariable Long id, @RequestParam(defaultValue = "1") int times) {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < times; i++) {
            userService.getUserById(id);
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
