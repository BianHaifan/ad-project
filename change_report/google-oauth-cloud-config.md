# 修改报告：云端 Google OAuth / Google Meet 部署配置

## 基本信息

- 执行者：opencode（代码实现者）
- 时间：2026-08-18
- 本包性质：**云端部署配置修复**，仅让 backend 容器收到 Google OAuth 五个配置变量；未改动业务逻辑、数据库迁移、Android、ML、Agent 或任何 Google OAuth / Google Meet 接口代码。
- 禁止且未改动：`backend/src/**`、`web/src/**`、`android/**`、`ml-service/**`、Flyway 迁移、OpenAPI、`backend/src/main/resources/application.yml`。

## 现象根因

- 云端 Recruiter Integration 页面显示 `Google integration is not configured in this demo environment yet.`
- 该提示来自 `web/src/components/GoogleConnectionSection.tsx`，由后端 `POST /api/v1/recruiter/google-oauth/authorize` 返回 `503 GOOGLE_OAUTH_NOT_CONFIGURED` 触发。
- 后端 `GoogleOAuthProperties.isConfigured()` 要求五个配置全部“齐全且合法”才放行：client ID、client secret、redirect URI、可解码为 32 字节 AES 密钥的加密密钥、且 web return URI 通过 HTTPS（或 loopback HTTP）校验。
- 根因：docker-compose 的 backend 容器和 Ansible 的 `.env` 模板此前都没有这五个变量，backend 收到空值 → fail-closed 显示未配置。

## 改动文件

1. `infra/docker/docker-compose.yml`
   - `backend.environment` 新增五条透传：
     `GOOGLE_OAUTH_CLIENT_ID`、`GOOGLE_OAUTH_CLIENT_SECRET`、`GOOGLE_OAUTH_REDIRECT_URI`、`GOOGLE_OAUTH_WEB_RETURN_URI`、`GOOGLE_TOKEN_ENCRYPTION_KEY`，均以 `${变量名}` 形式从宿主 `.env` 传入容器。
   - 未改动其他服务（mysql / ml / web）与其余环境变量。

2. `infra/ansible/templates/docker.env.j2`
   - 新增以上五个变量，取值来自 Ansible 变量（见下，由 Vault 供给）。保持既有 `DB_*`、`JWT_SECRET`、`ML_INTERNAL_TOKEN` 行原样。

3. `infra/ansible/playbook.yml`
   - `vars` 新增五个 Google OAuth 变量（默认空字符串），来源为 Ansible Vault 变量（`vault_google_oauth_*`）。
   - 删除 `Show generated env (for the record)`（`cat` `.env`）与 `Print env`（`debug`) 两个任务。
   - 已有服务器场景：读取现有 `.env`（`no_log`），对五个变量执行“缺键才追加”的 `lineinfile`；键已存在则不覆盖（保护真实值），文件权限仍由既有的 `Ensure .env is owned by ubuntu` 任务维持 `0600`。
   - 缺失 `.env` 场景：模板写入时一并带上五个变量。
   - 密钥相关任务全部加 `no_log: true`：生成 secrets、写 `.env`、写 `ML_INTERNAL_TOKEN`、读取现有 `.env`、五个 Google 变量的 `lineinfile`。

## 变量是否已由 Secret 管理

- 是。playbook 从 Ansible Vault 变量 `vault_google_oauth_client_id` 等读取（默认空字符串，为配置时跳过取值）。
- 仓库内未写入、也未新增任何真实密钥 / Client Secret / Token / 回调参数；模板仅含 Jinja 变量引用。
- 云端 `.env`（`/opt/adproject/infra/docker/.env`，权限 `0600`）由部署方按既定受保护方式填充真实值，不经 Git。

## 已执行验证

- `python yaml.safe_load`：`infra/ansible/playbook.yml` 与 `docker.env.j2` 语法均通过。
- `git status --short` 确认仅 3 个目标配置文件被修改，无其他模块改动。
- 本机无 docker / ansible-playbook，未执行 `docker compose config` 与语法检查，见下步。

## 待操作（无法在本环境执行，需部署方完成）

1. 在云服务器 `/opt/adproject/infra/docker/.env` 配置真实值：
   - `GOOGLE_OAUTH_CLIENT_ID`
   - `GOOGLE_OAUTH_CLIENT_SECRET`
   - `GOOGLE_OAUTH_REDIRECT_URI`（`https://<后端公网域名>/api/v1/recruiter/google-oauth/callback`）
   - `GOOGLE_OAUTH_WEB_RETURN_URI`（`https://<Recruiter网页域名>/recruiter/google-oauth`）
   - `GOOGLE_TOKEN_ENCRYPTION_KEY`（Base64 编码的 32 字节 AES 密钥）
2. Google Cloud Console 中该 OAuth Client 的 Authorized redirect URI 必须与 `GOOGLE_OAUTH_REDIRECT_URI` 完全一致（域名、HTTPS、路径、末尾斜杠均一致）。
3. 仅重建 backend 容器：
   ```bash
   docker compose -f docker-compose.yml up -d --no-deps --build backend
   docker compose -f docker-compose.yml ps
   docker compose -f docker-compose.yml logs backend
   ```
4. 验证：
   - 日志仅出现脱敏错误信息，不打印 `.env`、容器环境、Client Secret、加密密钥或 OAuth token。
   - Recruiter 登录后进入 Integration，点击 Connect Google → 收到 Google 授权页，而非 `GOOGLE_OAUTH_NOT_CONFIGURED`。
   - 完成授权后浏览器返回部署后的 `/recruiter/google-oauth`，状态显示 Connected。

## 限制

- 本环境无云服务器凭据，未执行真实容器重启与端到端 OAuth 验证；上述步骤需部署方在服务器执行。
- 对“键已存在但为空值”的 `.env` 行，playbook 为防覆盖不会自动补值（视为已存在），需人工填真实值后再重启；正常场景下旧 `.env` 完全缺失这些键，playbook 会自动补上。
- `GOOGLE_OAUTH_WEB_RETURN_URI` 必须为 HTTPS（或 loopback HTTP）；生产域名走 HTTPS，否则回调会被 `WebReturnUriValidator` 拒绝。

## 下一步（安全且最小）

1. 部署方在服务器填充真实值并重建 backend 后，回填本报告的“容器重启结果 / 验证结果”段落。
2. 由 CI 的 SAST / 既有测试确认无回归（本轮未触及业务代码，预期无影响）。