-- 案例广场分类：已有数据库执行本脚本（请用 utf8mb4）
-- mysql -u root -p --default-character-set=utf8mb4 positive_ai_code < sql/upgrade_app_category.sql

USE positive_ai_code;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS app_category
(
    id         bigint auto_increment comment 'id' primary key,
    name       varchar(64)                        not null comment '分类名称',
    sortOrder  int      default 0                 not null comment '排序，越小越靠前',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_category_name (name),
    INDEX idx_sortOrder (sortOrder)
) comment '应用分类标签' collate = utf8mb4_unicode_ci;

INSERT INTO app_category (name, sortOrder)
SELECT * FROM (
    SELECT '工具' AS name, 10 AS sortOrder UNION ALL
    SELECT '网站', 20 UNION ALL
    SELECT '数据分析', 30 UNION ALL
    SELECT '活动页面', 40 UNION ALL
    SELECT '管理平台', 50 UNION ALL
    SELECT '用户应用', 60 UNION ALL
    SELECT '个人管理', 70 UNION ALL
    SELECT '游戏', 80
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM app_category LIMIT 1);

-- 已有 categoryId 时下面会报错，可忽略
ALTER TABLE app
    ADD COLUMN categoryId bigint NULL COMMENT '案例广场分类 id' AFTER priority;

ALTER TABLE app
    ADD INDEX idx_categoryId (categoryId);
