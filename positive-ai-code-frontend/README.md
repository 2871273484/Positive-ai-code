# Positive AI 代码生成器 - 前端

基于 Vue 3 + TypeScript + Ant Design Vue 的前端。与后端配合，通过对话生成网站并实时预览、部署与管理。

## 功能特性

### 用户

- 主页一句话创建应用（快捷提示词 + 轮播占位）
- AI 对话生成，左侧日志 / 右侧预览（玻璃拟态风格）
- 我的应用：管理、删除
- 个人中心：资料与密码
- 案例广场：分类筛选、排序、查看更多
- 部署与下载代码

### 管理员

- 用户管理、应用管理
- 案例分类管理
- 应用精选与广场标签（最多 3 个；标签默认只读，点编辑后可改）

## 主要页面

| 路径 | 说明 |
| --- | --- |
| `/` | 主页：创建入口 + 我的作品 + 案例广场 |
| `/app/chat/:id` | 生成对话与预览 |
| `/my/apps` | 我的应用 |
| `/user/profile` | 个人中心 |
| `/admin/appManage` | 应用管理 |
| `/admin/categoryManage` | 案例分类 |
| `/admin/userManage` | 用户管理 |

## 技术栈

- Vue 3 + TypeScript + Vite
- Ant Design Vue、Vue Router、Pinia、Axios

## 本地开发

```bash
npm install
npm run dev
```

需同时启动后端（默认 `http://localhost:8080`）。更多整体说明见仓库根目录 [README.md](../README.md)。
