# Docker 部署说明

本目录用于部署当前应用。默认 Compose 只启动后端和前端 Nginx；Qdrant 通过 `rag` profile 按需启动。MySQL、Redis、RabbitMQ、PostgreSQL 通过 `deploy/.env` 连接已有服务，不会在本 Compose 中重复创建。

服务器基础设施的独立编排文件位于 `deploy/infrastructure/docker-compose.server.yml`，RabbitMQ 配置位于同目录的 `rabbitmq.conf`；应用 Compose 与基础设施 Compose 分开管理。

## 首次部署

1. 在服务器准备 Docker Engine 和 Docker Compose v2。
2. 在服务器准备环境变量文件 `deploy/.env`。本次部署使用环境变量注入，不上传本地 `application.yaml`，也不会将密钥打进镜像或提交 Git：

   ```bash
   cp deploy/.env.example deploy/.env
   chmod 600 deploy/.env
   ```

3. 确认 `deploy/application-docker.yaml`、`deploy/.env` 已在服务器，且 `nginx-1.18.0/html/hmdp/imgs` 目录可被挂载。
4. 构建并启动：

   ```bash
   docker compose -f docker-compose.app.yml --env-file deploy/.env config --quiet
   docker compose -f docker-compose.app.yml --env-file deploy/.env build
   docker compose -f docker-compose.app.yml --env-file deploy/.env up -d backend frontend
   ```

5. 检查状态和日志：

   ```bash
   docker compose -f docker-compose.app.yml --env-file deploy/.env ps
   docker compose -f docker-compose.app.yml --env-file deploy/.env logs -f --tail=200 backend
   ```

前端入口默认为服务器的 `8087` 端口。后端仅在 Compose 网络中暴露 `8081`，XXL-JOB 执行器端口按需暴露为 `9999`。

当前目标服务器内存约 3.6 GiB、无 Swap，且磁盘余量较低；Ollama 因系统 glibc 版本不兼容无法运行。因此默认关闭 RAG，不启动 Ollama/Qdrant。后端仍会保留 RAG 相关接口，但在未提供向量服务时不能执行知识库检索。

资源充足且准备好兼容的 Embedding 服务后，可单独启用 Qdrant：

```bash
docker compose -f docker-compose.app.yml --env-file deploy/.env --profile rag up -d qdrant
```

## 升级与回滚

升级前先保存当前镜像标签和配置。升级时执行 `build` 后再 `up -d`；若新版本异常，可使用升级前的镜像重新创建容器，并保留 `qdrant_data` 卷及上传目录。

不要把 `deploy/.env`、本地 `application.yaml` 或包含密钥的日志提交到 Git。

## RAG 文档监听

Docker 默认关闭文档监听，因为当前后端运行时镜像不包含 Python 和 `markitdown`。如果需要启用，应先提供带有对应运行时的镜像，再将 `RAG_DOCUMENT_ENABLED=true` 写入服务器的 `.env`。
