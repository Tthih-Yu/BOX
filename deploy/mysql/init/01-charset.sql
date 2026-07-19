-- MySQL 容器首次初始化脚本（挂载到 /docker-entrypoint-initdb.d 自动执行）。
-- 仅负责库级字符集与时区，表结构由后端首发时按 JPA 实体生成（见 DOCKER.md 首发流程）。

ALTER DATABASE material_pull
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 确保业务账号对该库有完整权限（compose 已通过环境变量创建账号，这里兜底授权）。
GRANT ALL PRIVILEGES ON material_pull.* TO 'material_pull'@'%';
FLUSH PRIVILEGES;
