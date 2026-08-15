# ML 模块 GitHub 提交与团队交接指南

本文档面向需要提交、审查、拉取或运行 ML 推荐模块的团队成员。当前在线模型是
`match-hgb-retrieval-v4`，调用链为：

```text
Android Candidate App
        ↓ HTTP
Spring Boot /api/v1/candidate/recommendations/jobs
        ↓ 私有 HTTP + X-Internal-Token
Python FastAPI + scikit-learn model.joblib
        ↓ 推荐快照
MySQL candidate_job_recommendations
```

Spring Boot 是唯一面向客户端的业务 API。Android 或 Web 不应直接调用 Python 服务，Python
服务也不直接读取 MySQL。

## 1. 提交到 GitHub 时必须包含什么

以下内容应和 ML 功能一起提交：

- `ml-service/src/ad_recommender/`：特征、训练、检索、评估和在线推理代码。
- `ml-service/tests/`：ML 单元测试和 API 契约测试。
- `ml-service/scripts/`：烟雾测试、反事实测试和冻结查询审计脚本。
- `ml-service/environment.yml`、`ml-service/pyproject.toml`：可复现的 Python 3.12 环境。
- `ml-service/artifacts/active/model.joblib`：当前获准上线的模型，约 1.95 MB。
- `ml-service/artifacts/active/manifest.json`：模型版本、特征版本、数据哈希和指标。
- `ml-service/reports/`、`ml-service/MODEL_CARD.md`：评估结果、限制和模型说明。
- 后端 recommendation 包、V11 migration、OpenAPI/环境示例及 Android 推荐页面代码。

`artifacts/active/model.joblib` 和 `manifest.json` 已被 `.gitignore` 特别放行。没有这两个文件，
队友虽然能安装 Python 包，但无法直接启动在线模型。

模型约 1.95 MB，可使用普通 Git；当前不需要 Git LFS。如果将来模型明显变大，应先在团队中
决定使用 Git LFS 或模型制品仓库，不要直接把大型二进制文件塞进普通 Git 历史。

## 2. 哪些内容不能提交

以下内容应继续由 `.gitignore` 排除：

- `.env`、真实数据库密码、JWT secret、`ML_INTERNAL_TOKEN`。
- `datasets/*.csv`、`datasets/*.parquet`。
- `ml-service/data/raw/`、`data/processed/`、`data/annotations/*.csv`。
- `ml-service/artifacts/teacher/`、`artifacts/archive/` 和其他实验模型。
- `.pytest_cache/`、`.ruff_cache/`、`__pycache__/`、日志和构建产物。
- `android/local.properties`，因为它只包含个人电脑的 Android SDK 路径。

原因如下：

1. 数据集和中间 JSONL/CSV 文件可达到几十或上百 MB，会迅速膨胀仓库。
2. 部分职位原始数据没有明确的再分发许可，不应公开上传。
3. 简历、标注数据可能包含个人信息或可识别文本，分享前必须先脱敏并确认许可。
4. 密钥和密码一旦进入 Git 历史，即使之后删除也仍可能被恢复。

推理只需要 `artifacts/active`，不需要训练 CSV、教师模型或 Top-300 文件。需要重新训练的成员，
应通过团队批准的私有存储获取已脱敏数据，并核对文件哈希和数据版本；不要通过公开 GitHub
仓库传递。

## 3. 提交前检查清单

仓库当前可能同时包含其他人的修改，因此不要直接执行 `git add .`。先检查范围：

```powershell
cd <你的仓库目录>
git status --short
git diff -- ml-service backend/src/main/java/com/adproject/recommendation `
  backend/src/main/resources/db/migration/V11__create_candidate_recommendations.sql `
  android/app/src/main/java/com/adproject/candidate
```

确认敏感文件和大型数据仍被忽略：

```powershell
git check-ignore -v .env android/local.properties `
  datasets/company.csv `
  ml-service/data/processed/training_pairs_retrieval_pseudo_v4.csv

git check-ignore -v `
  ml-service/artifacts/active/model.joblib `
  ml-service/artifacts/active/manifest.json
```

后两个命令应显示 `.gitignore` 中以 `!` 开头的放行规则。提交前还要确认暂存区：

```powershell
git diff --cached --stat
git diff --cached --check
git status --short
```

至少执行以下验证：

```powershell
cd ml-service
conda run -n ad-project-ml ruff check .
conda run -n ad-project-ml pytest -q

conda run -n ad-project-ml python scripts\evaluate_counterfactuals.py `
  --model artifacts\active\model.joblib `
  --report reports\counterfactual_v4.json
```

修改模型或特征时还必须运行 `smoke_test_model.py`、冻结查询审计和后端 recommendation 集成测试。
不要只以“服务能启动”作为验收标准。

## 4. 队友从 GitHub 拉取后如何直接运行

### 4.1 创建 Python 环境

```powershell
cd <仓库目录>\ml-service
conda env create -f environment.yml
```

如果环境已经存在：

```powershell
conda env update -n ad-project-ml -f environment.yml --prune
```

不要求先执行 `conda activate`，所有命令都可以使用 `conda run -n ad-project-ml ...`。

### 4.2 启动 ML 服务

团队成员自行选择一个本地共享令牌，同一台电脑上的 Python 与 Spring Boot 必须完全一致：

```powershell
cd <仓库目录>\ml-service
$env:ML_INTERNAL_TOKEN='只用于本地开发的共享令牌'
$env:ML_MODEL_PATH='artifacts\active\model.joblib'
conda run --no-capture-output -n ad-project-ml `
  ad-recommender serve --host 127.0.0.1 --port 8000
```

不要把这里的真实值写回 README、`.env.example` 或 Git。另开终端验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/internal/v1/health
```

响应应至少表示服务 ready，并对应 `match-hgb-retrieval-v4`。

### 4.3 启动 Spring Boot

复制 `.env.example` 的内容到自己未跟踪的 `backend/.env`，填写本机 MySQL、JWT 和 ML 配置：

```properties
ML_ENABLED=true
ML_BASE_URL=http://127.0.0.1:8000
ML_INTERNAL_TOKEN=和Python窗口完全相同的值
```

`ML_MODEL_PATH` 只在启动 Python 的窗口中配置；关键点是两边的 `ML_INTERNAL_TOKEN` 必须相同。
令牌不一致时，Python 会返回 401，Spring Boot 会按设计回退为 `FALLBACK/DEGRADED`。

### 4.4 启动客户端

招聘方 Web 默认运行在 `http://localhost:4173`。候选人推荐界面当前位于 Android App。

Android 模拟器通过 `10.0.2.2` 访问宿主机。后端如果运行在 8080，应这样构建：

```powershell
cd <仓库目录>\android
.\gradlew.bat :app:assembleDebug `
  '-PAD_API_BASE_URL=http://10.0.2.2:8080/api/v1/'
```

如果系统默认 Java 版本不兼容 Android Gradle 插件，请在 Android Studio 中选择 JDK 21，或给
Gradle 传入本机 JDK 21 的 `org.gradle.java.home`；不要把个人 JDK 绝对路径提交到仓库。

真实验证时，Android 推荐页应显示：

```text
ML model • match-hgb-retrieval-v4
Recommended #1
AI Match <score>%
```

同时，后端响应的 `meta` 应为：

```json
{
  "source": "MODEL",
  "modelStatus": "ACTIVE",
  "modelVersion": "match-hgb-retrieval-v4",
  "featureVersion": "pair-features-v1+prelabel-features-v3"
}
```

并在 MySQL 的 `candidate_job_recommendations` 中看到 `source=MODEL` 的推荐快照。

## 5. 更新模型时的团队约定

不要仅覆盖 `model.joblib`。每次批准新模型时应同步完成：

1. 修改模型版本，不能继续冒用旧版本号。
2. 同步提交新的 `model.joblib` 和 `manifest.json`。
3. 更新 `MODEL_CARD.md` 与对应评估报告。
4. 确认后端请求字段仍与模型特征契约兼容。
5. 运行单元测试、反事实测试、真实 API 烟雾测试和降级/恢复测试。
6. 在 PR 中说明训练数据版本、数据哈希、标签来源、指标变化和已知限制。
7. 保留冻结盲测组，禁止把它们加入训练或伪标注数据。

`joblib` 基于 Python pickle 机制。只能加载本仓库可信流程生成并经团队审查的模型文件，绝不加载
来自未知链接、聊天附件或未经验证分支的 `.joblib` 文件。

## 6. 如何向队友解释当前指标

当前 MAP@10、NDCG 等高分主要衡量学生模型与 AI teacher 标签的一致性，不等于真实招聘准确率，
也不能表述成“模型有 97% 的招聘成功率”。上线前仍需要独立人工盲测、公平性检查和真实用户反馈。

推荐分数表示候选人与职位特征的相对匹配程度，不是录用概率。不得将年龄、性别、种族、照片等
受保护或敏感属性作为推荐排序特征，也不能让模型自动做最终录用或淘汰决定。

## 7. 建议的 PR 说明模板

```text
ML version: match-hgb-retrieval-v4
Feature version: pair-features-v1+prelabel-features-v3
Active artifact included: yes
Manifest included: yes
Raw/private datasets included: no
Label source: AI teacher expected relevance + conservative hard negatives
Tests: ruff / pytest / counterfactual / backend integration / Android smoke
Fallback verified: yes
Known limitation: metrics are teacher-agreement metrics, not real hiring accuracy
```
