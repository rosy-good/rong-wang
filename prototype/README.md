# 融网学习平台 UI 原型

双击同目录 `index.html`，或在浏览器中打开该文件即可，无需安装依赖。

- 教师：`teacher` / `123456`
- 学生：`student` / `123456`
- 禁用演示：`disabled`
- 也可以点击快速演示入口，顶部可切换双端。

所有数据为演示数据，刷新清空。页面不会连接后端，不执行真实身份认证或生产判分。
详细评审路径和限制见 [UI_PROTOTYPE.md](../docs/UI_PROTOTYPE.md)。

本轮验收见 [UI_ACCEPTANCE_REPORT.md](../docs/UI_ACCEPTANCE_REPORT.md)。可用已安装的 Node.js 在项目根目录执行 `node prototype/acceptance.test.cjs`，运行无浏览器逻辑检查；该命令不验证页面布局或真实点击，不需要安装依赖。当前 UI V1 未冻结。
