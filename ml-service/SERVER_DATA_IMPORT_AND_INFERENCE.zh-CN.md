# 从 company.csv 导入服务器并进行 ML 推理

本文面向最终服务器联调，说明原始职位数据、后端数据库和 ML 模型之间的真实关系，以及哪些文件需要传到服务器。

## 1. 先理解真实数据流

```text
company.csv（只在导入阶段使用）
        ↓
ad-recommender import-jobs
        ↓ HTTPS，使用 RECRUITER 身份
Spring Boot 创建职位 + 发布职位 API
        ↓
MySQL jobs（ACTIVE + PUBLIC）
        ↓ Candidate 请求推荐
Spring Boot 读取候选人简历、偏好和最多 500 条可推荐职位
        ↓ 私有 HTTP + X-Internal-Token
Python ML 服务加载 model.joblib 并打分
        ↓
Spring Boot 返回推荐，并写入 candidate_job_recommendations
```

必须记住：

- ML 服务不读取 MySQL，也不读取 `company.csv`。
- Web/Android 不直接调用 ML 服务，只调用 Spring Boot。
- `company.csv` 导入成功后可以从服务器删除；在线推理依赖 MySQL 中的职位。
- 训练数据、Top-300 数据和教师模型都不是在线推理依赖。

## 2. company.csv 会怎样被抽取

导入工具执行以下步骤：

1. 读取 CSV，并检查必需列：`job_id`、`title`、`description`、`location`、`work_type`。
2. 删除缺少 ID、标题、描述的记录，并按 `job_id` 去重。
3. 只保留后端支持的 `FULL_TIME`、`INTERNSHIP`、`PART_TIME`。
4. 从 `title + description + skills_desc` 提取模型认识的技能。
5. 使用 `remote_allowed` 映射 `REMOTE/ONSITE`。
6. 按 employment type 和 workplace type 分桶，再根据 `--seed` 做确定性抽样。
7. 调用后端创建职位 API，再调用发布 API。

同一个 CSV、`--limit` 和 `--seed` 会选出相同职位。当前工具不会生成一个新的“小 CSV”，而是直接把选中的职位发送给后端。

可选薪资列为：`min_salary`、`max_salary`、`med_salary`、`currency`、`pay_period`。当前业务 API 只接受 SGD；非 SGD 薪资会作为未知薪资 `0-0 SGD` 导入，不会错误换算。

## 3. 推荐方案：CSV 留在本地，直接导入远程服务器

这是更安全的方案。服务器不需要保存原始 CSV，也不需要在服务器安装完整的数据处理环境。

### 3.1 前置条件

- 服务器的 MySQL、Spring Boot 和 ML 服务已经启动。
- Spring Boot 的公开地址使用 HTTPS，例如 `https://api.example.com`。
- 已有一个 `RECRUITER` 测试账号，其公司已由管理员审核为 `APPROVED`。
- 本地 Conda 环境 `ad-project-ml` 已安装。

### 3.2 先预览抽样结果

在本地 `ml-service` 目录运行：

```powershell
conda activate ad-project-ml

ad-recommender import-jobs `
  --jobs E:\data\company.csv `
  --backend-url https://api.example.com `
  --limit 20 `
  --seed 42 `
  --dry-run
```

这一步只读取文件并输出将要导入的职位，不登录、不写数据库。

### 3.3 导入真实服务器

为不同环境使用不同的 state 文件，避免把测试库和正式库的职位 ID 混在一起：

```powershell
$env:AD_IMPORT_PASSWORD='招聘方测试账号密码'

ad-recommender import-jobs `
  --jobs E:\data\company.csv `
  --backend-url https://api.example.com `
  --email dataset-recruiter@example.com `
  --limit 20 `
  --seed 42 `
  --state-file data\imports\server-company-jobs.json

Remove-Item Env:AD_IMPORT_PASSWORD
```

预期输出示例：

```json
{
  "selected": 20,
  "created": 20,
  "published": 20,
  "skipped": 0,
  "state_file": "data/imports/server-company-jobs.json"
}
```

再次运行时应看到 `created=0`、`published=0`、`skipped=20`。请私下备份 state 文件；它不应提交 Git，但丢失后重新导入可能产生重复职位。

## 4. 备选方案：把 CSV 临时上传到服务器再导入

只有服务器无法从你的本地网络访问、或团队明确要求服务器内执行时才使用该方案。

### 4.1 上传到受保护的临时目录

```bash
sudo install -d -m 700 -o "$USER" -g "$USER" /srv/ad-project-import
scp company.csv user@server:/srv/ad-project-import/company.csv
ssh user@server 'chmod 600 /srv/ad-project-import/company.csv'
```

不要把 CSV 放入 Web 静态目录、Docker build context、Git 仓库或公共对象存储。

### 4.2 在服务器安装并预览

```bash
cd /opt/ad-project/ml-service
conda env create -f environment.yml       # 首次运行
conda run -n ad-project-ml ad-recommender import-jobs \
  --jobs /srv/ad-project-import/company.csv \
  --backend-url http://127.0.0.1:8080 \
  --limit 20 \
  --seed 42 \
  --dry-run
```

### 4.3 执行导入

```bash
sudo install -d -m 700 -o "$USER" -g "$USER" /var/lib/ad-project-import
read -rsp 'Recruiter password: ' AD_IMPORT_PASSWORD
export AD_IMPORT_PASSWORD

conda run -n ad-project-ml ad-recommender import-jobs \
  --jobs /srv/ad-project-import/company.csv \
  --backend-url http://127.0.0.1:8080 \
  --email dataset-recruiter@example.com \
  --limit 20 \
  --seed 42 \
  --state-file /var/lib/ad-project-import/server-company-jobs.json

unset AD_IMPORT_PASSWORD
```

导入和复查完成后，可以删除服务器上的临时 CSV；保留受保护的 state 文件：

```bash
rm -- /srv/ad-project-import/company.csv
```

## 5. 服务器必须具备的 ML 运行文件

如果服务器通过 Git 拉取已合并的仓库，以下内容会随仓库到达，不必单独传输：

```text
ml-service/
├── environment.yml
├── pyproject.toml
├── src/ad_recommender/
└── artifacts/active/
    ├── model.joblib
    └── manifest.json
```

真正在线推理所需的核心是：推理代码、Python 依赖、`model.joblib` 和 `manifest.json`。当前模型约 1.95 MB，已跟踪在 Git 中。

服务器还需要通过 Secret/环境变量提供以下配置，不能写进 Git：

```properties
# ML 进程
ML_MODEL_PATH=/opt/ad-project/ml-service/artifacts/active/model.joblib
ML_INTERNAL_TOKEN=<随机生成的内部共享令牌>

# Spring Boot 进程
ML_ENABLED=true
ML_BASE_URL=http://127.0.0.1:8000
ML_INTERNAL_TOKEN=<与 ML 进程完全相同的令牌>
ML_CONNECT_TIMEOUT=2s
ML_READ_TIMEOUT=5s
ML_MAX_JOBS=500
```

如果 Spring Boot 和 ML 分别运行在 Docker 容器中，`ML_BASE_URL` 不能写 `127.0.0.1`，应使用同一 Docker network 中的服务名，例如 `http://ml:8000`。

ML 的 8000 端口必须保持私有，只允许 Spring Boot 访问；MySQL 端口也不应暴露到公网。

## 6. 启动和检查 ML 服务

在服务器上：

```bash
cd /opt/ad-project/ml-service
export ML_MODEL_PATH=/opt/ad-project/ml-service/artifacts/active/model.joblib
export ML_INTERNAL_TOKEN='<与后端一致的内部令牌>'

conda run --no-capture-output -n ad-project-ml \
  ad-recommender serve --host 127.0.0.1 --port 8000
```

另开终端检查：

```bash
curl --fail http://127.0.0.1:8000/internal/v1/health
```

健康响应应显示服务 ready，并加载 `match-hgb-retrieval-v4`。生产环境应使用 systemd、Docker 或其他进程管理器保持 ML 服务常驻，不要依赖临时 SSH 终端。

## 7. 验证职位已经进入服务器数据库

导入工具成功并不等于模型一定能看到职位。职位必须同时满足 `ACTIVE` 和 `PUBLIC`：

```sql
SELECT id, title, status, visibility, created_at
FROM jobs
WHERE status = 'ACTIVE' AND visibility = 'PUBLIC'
ORDER BY created_at DESC
LIMIT 20;
```

如果服务器使用 Docker MySQL，请在容器内部执行只读查询，不要为了检查而把 3306 暴露到公网。

## 8. 执行真实 ML 推理

1. 用 Candidate 测试账号登录 Web/Android。
2. 填写候选人简历、技能和工作偏好。
3. 打开职位推荐页面，或调用：

```http
GET https://api.example.com/api/v1/candidate/recommendations/jobs?limit=10
Authorization: Bearer <candidate-access-token>
```

成功使用模型时，响应应包含：

```json
{
  "meta": {
    "source": "MODEL",
    "modelStatus": "ACTIVE",
    "modelVersion": "match-hgb-retrieval-v4"
  }
}
```

如果 `meta.source` 是 `RULE_BASED` 或状态为 `DEGRADED`，依次检查：

1. ML health 是否 ready。
2. 后端 `ML_BASE_URL` 是否能从后端所在网络访问。
3. 两边的 `ML_INTERNAL_TOKEN` 是否完全一致。
4. 数据库是否存在 `ACTIVE + PUBLIC` 职位。
5. Candidate 是否已经保存简历和偏好。

最后确认推荐快照已写入数据库：

```sql
SELECT source, model_version, COUNT(*) AS result_count
FROM candidate_job_recommendations
GROUP BY source, model_version;
```

## 9. 到底要传什么到服务器

| 内容 | 是否需要 | 传输方式 | 是否长期保留 |
|---|---:|---|---:|
| 最新项目代码 | 必须 | 服务器 `git pull` 或部署制品 | 是 |
| `model.joblib` | 必须 | 已随 Git；也可从可信制品库部署 | 是 |
| `manifest.json` | 必须 | 与模型同时部署 | 是 |
| `environment.yml` / `pyproject.toml` | 必须 | 已随 Git | 是 |
| 后端 JAR/镜像、Web 镜像 | 按部署方式 | CI/CD 或服务器构建 | 是 |
| `company.csv` | 二选一 | 推荐留在本地远程导入；否则 SCP 临时上传 | 否 |
| 导入 state JSON | 必须保留一份 | 保存在执行导入的机器，私下备份 | 是，但不进 Git |
| DB/JWT/ML secrets | 必须 | GitHub Secrets、systemd EnvironmentFile 或 Secret Manager | 是，但不进 Git |
| MySQL 持久卷/备份 | 必须 | 服务器基础设施 | 是 |
| 训练 pairs、Top-300、伪标签 CSV/JSONL | 不需要 | 不上传 | 否 |
| teacher 模型、归档模型 | 不需要推理 | 只进受控模型仓库 | 否 |
| 原始简历、人工标注数据 | 不需要推理 | 不上传 | 否 |

最小结论：如果项目代码和 active 模型已经通过 Git 部署，你通常只需要额外配置服务器 secrets，并在本地运行一次导入命令。`company.csv` 不必上传服务器。

## 10. 上线前检查清单

- [ ] 只使用有权使用和再处理的职位数据。
- [ ] 先 `--dry-run`，再用小 `--limit` 导入测试库。
- [ ] Recruiter 公司状态为 `APPROVED`。
- [ ] 重复导入显示 `skipped`，没有创建重复职位。
- [ ] MySQL 中存在 `ACTIVE + PUBLIC` 职位。
- [ ] ML health 显示正确模型版本。
- [ ] 后端与 ML 的内部令牌一致，8000/3306 不暴露公网。
- [ ] Candidate 已保存简历和偏好。
- [ ] 推荐响应为 `MODEL + ACTIVE`。
- [ ] `candidate_job_recommendations` 中存在模型快照。
- [ ] 临时 CSV 已删除或转入受控私有存储，state 文件已私下备份。
