# 修改报告：本地 Web API 代理端口修正

## 完成内容

- 将 Vite 开发服务器 `/api` 代理目标从 `http://localhost:8080` 改为本项目 Docker 后端实际暴露的 `http://localhost:8081`。
- 原 `8080` 被无关的 WeKnora 服务占用，导致招聘者登录页收到非本项目 JSON，并显示“unexpected response”。
- 已重启 Web 开发服务器，并通过 `GET /api/v1/recruiter/dashboard` 验证代理返回本项目预期的未登录 `401`。

## 修改模块

- `web/vite.config.ts`

## API / 数据库

- 无 API 契约或数据库变化；仅本地开发代理地址修正。

## 限制与下一步

- 用户需刷新浏览器页面后重新登录。
- Vite 代理仅影响本地开发服务器；生产部署的反向代理配置不受影响。
