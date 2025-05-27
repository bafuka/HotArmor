package cn.bafuka.hotarmor.example.service;

import cn.bafuka.hotarmor.annotation.HotArmorCache;
import cn.bafuka.hotarmor.annotation.HotArmorEvict;
import cn.bafuka.hotarmor.example.entity.User;
import cn.bafuka.hotarmor.example.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务
 * 演示 HotArmor 的使用
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据ID查询用户（使用HotArmor缓存）
     * 这个方法会经过三级漏斗防护
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    @HotArmorCache(resource = "user:detail", key = "#userId")
    public User getUserById(Long userId) {
        log.info("从数据库查询用户: userId={}", userId);
        // 模拟数据库查询延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return userMapper.selectById(userId);
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    @HotArmorCache(resource = "user:byUsername", key = "#username")
    public User getUserByUsername(String username) {
        log.info("从数据库查询用户: username={}", username);
        return userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    public List<User> getAllUsers() {
        return userMapper.selectList(null);
    }

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建的用户
     */
    public User createUser(User user) {
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户创建成功: userId={}", user.getId());
        return user;
    }

    /**
     * 更新用户（使用HotArmor失效策略）
     * 会自动清理缓存并保证一致性
     *
     * @param user 用户信息
     */
    @HotArmorEvict(resource = "user:detail", key = "#user.id")
    public void updateUser(User user) {
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("用户更新成功: userId={}", user.getId());
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     */
    @HotArmorEvict(resource = "user:detail", key = "#userId")
    public void deleteUser(Long userId) {
        userMapper.deleteById(userId);
        log.info("用户删除成功: userId={}", userId);
    }
}
