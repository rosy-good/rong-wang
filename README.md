# 融网学习平台

融网学习平台采用前后端分离单体架构。需求与 UI 原型已经完成，正式工程骨架于 2026-11-13 初始化。

## 目录

- `frontend/`：Vue 3 + TypeScript + Vite 前端。
- `backend/`：Java 17 + Spring Boot + MyBatis-Plus 后端。
- `prototype/`：HTML/CSS/JavaScript UI 原型。
- `docs/`：需求、数据库、API、开发计划与验收文档。

## 本地运行

前端：

```text
cd frontend
npm install
npm run dev
```

后端：

```text
cd backend
mvn spring-boot:run
```

当前后端默认不连接数据库，以便先验证工程启动。启用 MySQL 前，应确定本地配置方式并移除 `application.yml` 中对应的自动配置排除项。不要提交真实数据库密码。
