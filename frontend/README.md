# 融网学习平台前端

当前目录包含 Vue 3 + TypeScript + Vite 工程骨架，并已接入 Element Plus、Vue Router、Pinia 和 Axios。业务页面尚未实现。

## 常用命令

- `npm run dev`：启动本地开发服务器，默认地址为 `http://localhost:5173`。
- `npm run build`：执行 TypeScript 检查并构建生产文件。
- `npm run preview`：预览已经构建的文件。

开发服务器会把 `/api` 请求转发到 `http://localhost:8080`。Axios 的基础路径为 `/api/v1`，并启用 Cookie，以便后续接入 Session 方案时复用；认证方案仍待工程设计确认。
