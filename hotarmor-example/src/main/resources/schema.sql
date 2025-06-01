-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    age INT COMMENT '年龄',
    status INT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    price DECIMAL(10, 2) NOT NULL COMMENT '商品价格',
    stock INT DEFAULT 0 COMMENT '库存数量',
    category VARCHAR(50) COMMENT '分类',
    status INT DEFAULT 1 COMMENT '状态：0-下架，1-上架',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 插入测试数据
INSERT INTO user (username, nickname, email, phone, age, status) VALUES
('zhangsan', '张三', 'zhangsan@example.com', '13800138000', 25, 1),
('lisi', '李四', 'lisi@example.com', '13800138001', 30, 1),
('wangwu', '王五', 'wangwu@example.com', '13800138002', 28, 1),
('zhaoliu', '赵六', 'zhaoliu@example.com', '13800138003', 35, 1),
('sunqi', '孙七', 'sunqi@example.com', '13800138004', 22, 1);

INSERT INTO product (name, description, price, stock, category, status) VALUES
('iPhone 14 Pro', '苹果旗舰手机，A16芯片', 7999.00, 100, '手机', 1),
('MacBook Pro', '16英寸专业笔记本，M2 Pro芯片', 19999.00, 50, '电脑', 1),
('AirPods Pro', '主动降噪无线耳机', 1899.00, 200, '耳机', 1),
('iPad Air', '10.9英寸平板电脑', 4399.00, 150, '平板', 1),
('Apple Watch', '智能手表，健康监测', 2999.00, 80, '手表', 1),
('小米13 Pro', '骁龙8 Gen2旗舰手机', 4999.00, 300, '手机', 1),
('华为MateBook', '商务办公笔记本', 5999.00, 120, '电脑', 1),
('索尼WH-1000XM5', '头戴式降噪耳机', 2399.00, 100, '耳机', 1),
('三星Galaxy S23', '安卓旗舰手机', 5999.00, 200, '手机', 1),
('戴尔XPS 13', '轻薄商务本', 7999.00, 80, '电脑', 1);
