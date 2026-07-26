/**
 * 环境变量配置说明
 *
 * 在项目根目录创建 .env.local 或 .env.development 文件，并添加以下配置：
 *
 * # 已部署站点访问前缀（与后端 code.deploy-host 一致）
 * VITE_DEPLOY_DOMAIN=http://localhost:8080/api/deployed
 *
 * # API 基础地址
 * VITE_API_BASE_URL=http://localhost:8080/api
 *
 * 生产环境可以创建 .env.production 文件：
 *
 * VITE_DEPLOY_DOMAIN=/api/deployed
 * VITE_API_BASE_URL=/api
 */

export {}
