# 将原始职位导入后端并进行真实模型推理

这个工具用于本地或测试服务器联调。数据流是：

```text
company.csv
  -> ad-recommender import-jobs
  -> Spring Boot 招聘方创建/发布职位 API
  -> MySQL jobs 表
  -> 候选人推荐 API
  -> Python ML 服务
  -> 推荐结果写回 MySQL 并返回前端
```

Python 工具不会直接连接或修改数据库。所有职位都经过后端 API，因此鉴权、公司审核状态、字段校验和职位发布规则都会生效。

## 前置条件

1. MySQL、Spring Boot 后端和 ML 服务已经启动。
2. 准备一个 `RECRUITER` 账号，并确保其公司状态为 `APPROVED`。
3. 准备原始职位文件，例如 `datasets/company.csv`。
4. 已创建并激活 Conda 环境：`conda activate ad-project-ml`。

原始职位数据受体积、隐私或授权限制，默认不会跟随 Git 仓库下载。如果你的仓库中没有 `datasets/company.csv`，请自行取得有权使用的数据，并把 `--jobs` 改为该 CSV 的绝对路径。

## 第一步：只预览，不写数据库

在 `ml-service` 目录执行：

```powershell
ad-recommender import-jobs `
  --jobs ..\datasets\company.csv `
  --backend-url http://127.0.0.1:8080 `
  --limit 20 `
  --seed 42 `
  --dry-run
```

工具会从不同的 employment type 和 workplace type 中确定性抽样。相同文件、`limit` 和 `seed` 会得到相同结果，方便重复测试。

## 第二步：通过真实后端 API 导入并发布

密码只放在当前终端的环境变量中，不要写进命令、代码、文档或 Git：

```powershell
$env:AD_IMPORT_PASSWORD='你的招聘方密码'

ad-recommender import-jobs `
  --jobs ..\datasets\company.csv `
  --backend-url http://127.0.0.1:8080 `
  --email your-recruiter@example.com `
  --limit 20 `
  --seed 42

Remove-Item Env:AD_IMPORT_PASSWORD
```

成功输出中的 `created` 是新建数量，`published` 是成功发布数量，`skipped` 是此前已完成、此次跳过的数量。

默认进度文件是 `data/imports/company-jobs.json`。它记录原始职位 ID 与后端职位 ID 的对应关系，并已被 `.gitignore` 忽略。重复执行相同命令时，工具会跳过已完成职位；如果上次创建成功但发布中断，下次会继续发布，不会重复创建。

如只想创建草稿，添加 `--no-publish`。也可以用 `AD_IMPORT_ACCESS_TOKEN` 提供现有访问令牌，此时无需邮箱和密码。

## 第三步：确认数据库中有可推荐职位

推荐服务只读取 `status = ACTIVE` 且 `visibility = PUBLIC` 的职位。可在 MySQL 中执行只读检查：

```sql
SELECT id, title, status, visibility
FROM jobs
ORDER BY created_at DESC
LIMIT 20;
```

原数据中的非 SGD 薪资无法无损写入当前后端契约，所以导入工具将其保存为 `0–0 SGD`，表示薪资未知，避免把美元数值错误地当成新币。

## 第四步：执行真实模型推理

先用 Candidate 账号在前端填写简历与偏好，然后打开推荐页面；或直接请求后端：

```http
GET /api/v1/candidate/recommendations/jobs?limit=10
Authorization: Bearer <candidate-access-token>
```

响应中的推荐列表位于 `data`，推理元数据位于 `meta`。重点检查：

- `meta.source` 应为 `MODEL`，而不是降级规则结果；
- `meta.modelStatus` 应为 `ACTIVE`；
- `meta.modelVersion` 应与 ML 服务启动时加载的模型一致；
- 推荐结果应包含刚导入的 ACTIVE/PUBLIC 职位；
- 相同候选人再次请求时，推荐快照会保存在后端数据库中。

可以用下面的 SQL 确认推理结果已持久化：

```sql
SELECT source, model_version, COUNT(*) AS result_count
FROM candidate_job_recommendations
GROUP BY source, model_version;
```

## 安全与服务器注意事项

- 只导入有权使用的数据，不要把来源不明的完整原始数据上传到 GitHub。
- 先在测试数据库用较小的 `--limit` 验证，不要直接写生产库。
- 不提交密码、access token、导入进度文件和含个人信息的数据。
- 后端当前一次最多向模型提供 500 个 ACTIVE/PUBLIC 职位；导入更多职位不等于一次推理会使用全部职位。
- 若公司未审核通过，工具会主动停止；应通过正常管理员审核流程批准测试公司，而不是在生产库手改状态。

## 常见错误

- `Cannot reach backend`：后端没有启动，或 `--backend-url` 错误。
- `company must be APPROVED`：招聘方公司尚未审核通过。
- `401/403`：账号、密码、令牌或角色不正确。
- `VERSION_CONFLICT`：职位状态已经被其他操作修改；确认后再用新的进度状态执行。
- 返回 `RULE_BASED`：检查 ML 服务是否在 8000 端口运行，以及后端和 ML 的内部令牌是否一致。
