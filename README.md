# Positive AI Code

一句话生成网站：Spring Boot + Vue 3 的 AI 零代码应用生成平台。

## 功能概览

- **对话生成**：支持 HTML / 原生多文件 / Vue 项目三种模式，SSE 流式出码与右侧实时预览
- **案例广场**：精选应用展示，分类标签（最多 3 个）、排序与分页
- **个人中心**：资料修改、密码修改；「我的应用」管理与删除
- **管理后台**：用户管理、应用管理、案例分类管理（仅管理员）
- **部署与封面**：一键部署；代码落盘后异步截取首页作列表封面（与预览 iframe 无关）
- **固定品牌 Logo**：生成站统一注入项目内吉祥物 Logo；本站顶栏使用豆包图标

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、MyBatis-Flex、LangChain4j、LangGraph4j、Redis、MySQL、COS |
| 前端 | Vue 3、TypeScript、Vite、Ant Design Vue、Pinia |

## 目录结构

```
Positive-ai-code/
├── src/                          # Spring Boot 后端
├── positive-ai-code-frontend/    # Vue 前端
├── sql/                          # 建表与升级脚本
│   ├── create_table.sql
│   ├── upgrade_app_category.sql
│   └── upgrade_app_category_rel.sql
└── README.md
```

## 快速开始

### 环境要求

- JDK 21（`JAVA_HOME` 需指向 21，勿用 17/8 编译）
- Node.js 18+
- MySQL 8、Redis
- （可选）本机 Chrome，用于封面截图

### 数据库

```bash
# 新库可直接执行
mysql -u root -p < sql/create_table.sql

# 已有库升级分类相关表
mysql -u root -p positive_ai_code < sql/upgrade_app_category.sql
mysql -u root -p positive_ai_code < sql/upgrade_app_category_rel.sql
```

### 后端

```bash
# Windows 示例：指定 JDK 21
set JAVA_HOME=C:\Program Files\Java\jdk-21
mvnw.cmd -DskipTests spring-boot:run
# 或 IDEA 运行 PositiveAiCodeApplication，Active Profile: local
```

配置见 `src/main/resources/application-local.yml`（勿提交密钥）。

### 前端

```bash
cd positive-ai-code-frontend
npm install
npm run dev
```

默认接口代理到后端 `http://localhost:8080/api`。

## 生成与预览说明

1. **流式生成**：准备素材（短超时，失败跳过）→ 连接模型出码 → 落盘  
2. **右侧预览**：直接 iframe 打开已生成的静态站点，不依赖封面  
3. **封面图**：后台异步 Selenium 截首页 → 压缩 → 上传 COS，供广场/列表缩略图  

若生成中途长时间无新 token，后端约 35s 空闲超时并尽量保存已生成内容。

## 相关文档

- 前端说明：[positive-ai-code-frontend/README.md](positive-ai-code-frontend/README.md)
