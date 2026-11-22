# 融网学习平台前端

当前目录包含 Vue 3 + TypeScript + Vite 正式前端。登录、教师课程与题库、任务草稿与发布、成绩统计，以及学生入课、答题、历史、错题与复练页面已连接后端接口。正式判分和权限校验由后端完成。

## 常用命令

- `npm run dev`：启动本地开发服务器，默认地址为 `http://localhost:5173`。
- `npm run build`：执行 TypeScript 检查并构建生产文件。
- `npm run preview`：预览已经构建的文件。

开发服务器会把 `/api` 请求转发到 `http://localhost:8080`。Axios 的基础路径为 `/api/v1`，并启用 Cookie，用于已确认的服务端 Session 认证。

先启动本地 MySQL 和后端，再运行 `npm run dev` 并打开 `http://localhost:5173`。使用已预创建的教师或学生账号登录。前端不保存账号密码；写请求自动携带 Session 对应的 CSRF 令牌。Q12 自动保存尚未确认，答题离开时仅弹出未提交答案提示。
