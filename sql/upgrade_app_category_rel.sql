-- 案例多标签关联（最多 3 个）
USE positive_ai_code;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS app_category_rel
(
    id         bigint auto_increment comment 'id' primary key,
    appId      bigint                             not null comment '应用 id',
    categoryId bigint                             not null comment '分类 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    UNIQUE KEY uk_app_category (appId, categoryId),
    INDEX idx_rel_appId (appId),
    INDEX idx_rel_categoryId (categoryId)
) comment '应用-分类关联' collate = utf8mb4_unicode_ci;

-- 把旧的单分类迁移到关联表
INSERT IGNORE INTO app_category_rel (appId, categoryId)
SELECT id, categoryId FROM app
WHERE categoryId IS NOT NULL AND isDelete = 0;
