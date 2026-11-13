# 融网学习平台后端

当前目录只包含 Spring Boot 工程骨架，不含课程、任务、判分等业务实现。

## 常用命令

- `mvn test`：编译并运行基础上下文测试。
- `mvn spring-boot:run`：在本地启动后端，默认地址为 `http://localhost:8080/api`。
- `mvn package`：生成可运行的 JAR。

MySQL 与 MyBatis-Plus 自动配置暂时关闭，待数据库连接参数及工程设计确认后再启用。密码应通过环境变量或本地配置提供，不得提交到仓库。
