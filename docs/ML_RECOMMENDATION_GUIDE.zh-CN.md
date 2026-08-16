# ML 职位匹配：架构、训练与真实测试指南

这份指南对应仓库中的推荐系统及其AI伪标签v2训练流程。它解决两个问题：

1. 求职者登录后，根据简历和求职偏好获得职位排序。
2. 招聘方的候选人排序先保留为演示界面；Python 服务已经提供候选人排序契约，后续接入真实候选池时复用同一套特征。

模型只给出辅助排序和可解释证据，不自动录用或拒绝任何人，也不使用姓名、邮箱、年龄等敏感或代理特征。

## 1. 先理解整体调用链

```text
Android Candidate App
        |
        | JWT + public REST API
        v
Spring Boot :8080  --------------------> MySQL :3306
  | 读取简历、偏好、已发布职位             保存业务数据和推荐快照
  |
  | X-Internal-Token + private JSON
  v
FastAPI ML service :8000
  | 特征提取 -> scikit-learn 模型 -> 排序 -> 解释
  v
artifacts/active/model.joblib
```

重要边界：Android/Web 永远不直接访问 Python；Spring Boot 是唯一公开业务 API。这样 JWT、权限、数据库事务和降级策略仍由后端统一控制。

## 2. 代码阅读路线

建议按一次请求真正经过的顺序阅读：

1. `android/.../JobPreferencesScreen.kt`：求职者填写职位、地点、工作方式和最低薪资。
2. `android/.../HttpApis.kt`：Android 调用偏好及推荐 API。
3. `backend/.../recommendation/api/CandidateRecommendationController.java`：公开 API 入口和 Candidate 权限。
4. `backend/.../recommendation/application/CandidateRecommendationService.java`：组装简历、偏好和职位，调用模型，失败时降级。
5. `backend/.../recommendation/application/MlRecommendationClient.java`：Spring 与私有 FastAPI 的 JSON 契约和内部令牌。
6. `ml-service/src/ad_recommender/api.py`：FastAPI 健康检查和两个排序端点。
7. `ml-service/src/ad_recommender/features.py`：真正决定模型“看见什么”的特征工程。
8. `ml-service/src/ad_recommender/prelabeling.py`：v2教师模型的29维特征和全量伪标注。
9. `ml-service/src/ad_recommender/model.py`：scikit-learn 训练、推理和模型包。
10. `ml-service/src/ad_recommender/evaluation.py`：Precision@5、Recall@10、NDCG@10、MAP@10。
11. `backend/.../RecommendationSnapshotService.java`：把本轮 Top-N 和版本保存到 MySQL。
12. `backend/.../job/application/CandidateJobQueryService.java`：普通职位列表/详情只显示仍然有效的推荐分数。

数据库结构在 `backend/src/main/resources/db/migration/V13__create_candidate_recommendations.sql`。公开接口定义在 `docs/openapi-v1.yaml`。

## 3. 本次创建的 Conda 环境

本机已创建环境 `ad-project-ml`，固定使用 Python 3.12 和 scikit-learn 1.9.0。重新打开终端后，先执行：

```powershell
cd E:\githubitem\AD\ad-project\ml-service
conda activate ad-project-ml
python --version
python -c "import sklearn; print(sklearn.__version__)"
```

如果 PowerShell 还不能执行 `conda activate`，可以不激活，所有命令改用：

```powershell
conda run -n ad-project-ml <要执行的命令>
```

在另一台电脑第一次创建环境：

```powershell
cd E:\githubitem\AD\ad-project\ml-service
conda env create -f environment.yml
```

## 4. 数据和标签是怎样生成的

原始简历和职位 CSV、训练对、人工复核表、模型文件都被 `.gitignore` 排除，不会被错误提交到 GitHub。

当前本机数据规模：

- 简历：2,483 条。
- 职位：25,298 条。
- 原始弱监督配对：74,170 对。
- 冻结盲测：20 个 Candidate 组、597 对，整组不进入最终训练。
- 全职位 Top-300 召回：744,900 对。
- v3教师连续伪标签训练对：738,900 对，冻结组排除后包含 2,463 个 Candidate 组。
- 2/3 分正样本：212,559 对；至少有一个正样本的训练组覆盖率为 99.35%。

完整重做数据准备：

```powershell
cd E:\githubitem\AD\ad-project\ml-service

conda run -n ad-project-ml ad-recommender download-resumes `
  --output ..\datasets\resume_full.csv

conda run -n ad-project-ml ad-recommender prepare-data `
  --resumes ..\datasets\resume_full.csv `
  --jobs ..\datasets\company.csv `
  --output data\processed\training_pairs.csv `
  --annotations data\annotations\human_review_300.csv
```

`human_review_300.csv` 里的弱标签只用于抽样提示。人工标注者应独立填写 `human_relevance_0_to_3`，不能直接复制弱标签。获得足够人工标签后，再让训练脚本优先使用人工判断，这是下一阶段提高真实效果的关键。

## 5. 全职位召回、连续伪标注和最终模型

v4 先清洗 25,298 个职位，再用 TF-IDF 为每位 Candidate 召回 Top-300，并采用“29维可解释特征 + 单调约束 HistGradientBoostingRegressor”学习教师连续期望相关度与保守 Hard Negative 修正目标。主要特征包括：

- 简历与职位描述的 TF-IDF 余弦相似度。
- 技能集合覆盖率、共同技能数和缺失技能数。
- 期望职位标题与职位标题的相似度。
- 地点、workplace type、employment type 是否符合偏好。
- 最低薪资是否满足。
- 简历经验文本和职位内容的词项重合。

召回、伪标注和最终训练命令：

```powershell
cd E:\githubitem\AD\ad-project\ml-service

conda run -n ad-project-ml ad-recommender retrieve `
  --candidate-pairs data\processed\training_pairs_v4.csv `
  --jobs ..\datasets\company.csv `
  --output data\processed\training_pairs_retrieval_top300_v4.csv `
  --report reports\retrieval_top300_v4.json `
  --top-k 300

conda run -n ad-project-ml ad-recommender pseudo-label `
  --pairs data\processed\training_pairs_retrieval_top300_v4.csv `
  --teacher-model artifacts\teacher\prelabeler-distilled-v2.joblib `
  --output data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --exclude-query-ids data\processed\frozen_blind_query_ids.txt `
  --report reports\pseudo_label_retrieval_v4.json `
  --target expected

conda run -n ad-project-ml ad-recommender train `
  --pairs data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --algorithm hgb-guarded-regressor `
  --label-source AI_TEACHER_EXPECTED_RELEVANCE_V2+CONSERVATIVE_HARD_NEGATIVES_V1 `
  --artifact-dir artifacts\active `
  --report reports\training_retrieval_v4.json
```

当前验证集结果如下；数值越高越好：

| 指标 | 规则基线 | 训练模型 |
|---|---:|---:|
| Precision@5 | 0.5254 | 0.9566 |
| Recall@10 | 0.0811 | 0.2616 |
| NDCG@10 | 0.6244 | 0.9761 |
| MAP@10（全部查询） | 0.3998 | 0.9674 |
| MAP@10（存在正样本） | 0.4039 | 0.9773 |
| 正样本查询覆盖率 | 0.9899 | 0.9899 |

Recall@10 较低是因为每个查询的 300 个召回职位中可能有很多 2/3 分职位，而 Top-10 最多只能命中 10 个；它不应单独用于否定排序质量。这些指标表示最终模型对AI教师伪标签排序的拟合程度，并不等于真实招聘准确率。当前 `human_review_300.csv` 的人工标签列仍为空，冻结盲测也尚未获得独立人工标签；上线前必须补完该评估、监控不同群体的误差，并让招聘决定始终由人完成。

冻结组教师一致性审计命令：

```powershell
conda run -n ad-project-ml python scripts\evaluate_frozen_queries.py `
  --model artifacts\active\model.joblib `
  --teacher-model artifacts\teacher\prelabeler-distilled-v2.joblib `
  --retrieval-pairs data\processed\training_pairs_retrieval_top300_v4.csv `
  --training-pairs data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --query-ids data\processed\frozen_blind_query_ids.txt `
  --report reports\frozen_teacher_audit_v4.json
```

审计确认 20 个冻结组与训练集重叠为 0，并在新召回的 6,000 对上得到教师一致性 MAP@10 0.9917、正样本查询覆盖率 1.0。这个结果只说明学生模型能在未参与训练的 Candidate 组上复现教师判断，不能代替独立人工盲测。

## 6. 启动三个服务

需要三个 PowerShell 窗口。

### 窗口 A：MySQL

确认 MySQL 服务已启动，并且本地数据库、用户已经按项目配置创建。不要把真实密码写入 Git。

### 窗口 B：Python ML 服务

```powershell
cd E:\githubitem\AD\ad-project\ml-service
$env:ML_INTERNAL_TOKEN='请换成你自己的本地共享令牌'
$env:ML_MODEL_PATH='artifacts\active\model.joblib'
conda run -n ad-project-ml ad-recommender serve --port 8000
```

看到 `Uvicorn running on http://127.0.0.1:8000` 后不要关闭窗口。

### 窗口 C：Spring Boot

```powershell
cd E:\githubitem\AD\ad-project\backend
$env:DB_URL='jdbc:mysql://localhost:3306/adproject?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:DB_USERNAME='adproject'
$env:DB_PASSWORD='你的本地数据库密码'
$env:JWT_SECRET='至少32字节且只在本地保存的随机字符串'
$env:ML_ENABLED='true'
$env:ML_BASE_URL='http://127.0.0.1:8000'
$env:ML_INTERNAL_TOKEN='和窗口B完全相同的本地共享令牌'
mvn spring-boot:run
```

如果终端提示找不到 `mvn`，就使用 IDE 的 Spring Boot Run 按钮，环境变量仍填写相同值。

启动时 Flyway 会自动执行 V13 migration。看到后端监听 `8080` 后不要关闭窗口。

## 7. 先检查 ML 健康状态

在第四个 PowerShell 窗口执行：

```powershell
Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8000/internal/v1/health'
```

预期看到 `status=ready` 和模型版本。健康检查不接收业务数据，可以无令牌访问；两个推荐 POST 端点必须携带内部令牌，否则返回 401。

## 8. 用真实 Candidate 账号测试

先在 Candidate Android App 或认证 API 中注册/登录一个 Candidate。以下 PowerShell 示例把登录结果保存在当前终端变量中：

```powershell
$loginBody = @{
  email = '你的Candidate邮箱'
  password = '你的密码'
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/login' `
  -ContentType 'application/json' `
  -Body $loginBody

$auth = @{ Authorization = "Bearer $($login.data.accessToken)" }
```

`$login` 和 `$auth` 只是这个 PowerShell 进程的内存变量；新开终端不会自动拥有它们，需要重新登录。浏览器/Android 能跨页面保持登录，是因为客户端把 token 保存到了自己的 session/local storage，而不是因为 PowerShell 变量跨终端共享。

### 8.1 保存简历（必须有简历才能推荐）

第一次创建用 `expectedVersion=0`；修改已有简历时先 GET 当前版本，再把那个版本传回来。

```powershell
$resume = @{
  fullName = 'ML Test Candidate'
  age = 24
  location = 'Singapore'
  headline = 'Backend Software Engineer'
  summary = 'Builds Java Spring Boot APIs and Python data services.'
  skills = @('Java', 'Spring Boot', 'Python', 'SQL', 'REST API')
  experiences = @()
  expectedVersion = 0
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Put `
  -Uri 'http://localhost:8080/api/v1/candidate/resume' `
  -Headers $auth -ContentType 'application/json' -Body $resume
```

### 8.2 保存求职偏好

```powershell
$preference = @{
  desiredTitles = @('Backend Engineer', 'Java Developer')
  preferredLocations = @('Singapore')
  workplaceTypes = @('HYBRID', 'REMOTE')
  employmentTypes = @('FULL_TIME')
  minimumSalary = 5000
  salaryCurrency = 'SGD'
  salaryPeriod = 'MONTH'
  expectedVersion = 0
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Put `
  -Uri 'http://localhost:8080/api/v1/candidate/job-preferences' `
  -Headers $auth -ContentType 'application/json' -Body $preference
```

### 8.3 请求推荐

```powershell
$result = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/candidate/recommendations/jobs?limit=10' `
  -Headers $auth

$result.meta
$result.data | Select-Object rank, title, companyName, matchScore
```

正常情况下 `meta.source` 为 `MODEL`。停止 Python 服务后再请求一次，应仍返回结果，但 `meta.source` 变成 `FALLBACK`；这证明 ML 故障不会让职位功能整体不可用。

### 8.4 检查解释和快照失效

```powershell
$result.data[0].matchAnalysis
```

解释应包含 `strongMatches`、`gaps` 和 `evidence`。推荐后打开同一职位详情也会看到当前分数。随后修改简历、偏好或职位版本，旧分数不会继续显示；重新请求推荐后才会生成新的快照。

## 9. 自动化测试

Python：

```powershell
cd E:\githubitem\AD\ad-project\ml-service
conda run -n ad-project-ml ruff check .
conda run -n ad-project-ml pytest
```

Spring Boot：

```powershell
cd E:\githubitem\AD\ad-project\backend
mvn test
```

Android（需要 JDK 21 和 Android SDK）：

```powershell
cd E:\githubitem\AD\ad-project\android
$env:JAVA_HOME='C:\Users\12917\.jdks\ms-21.0.11'
$env:ANDROID_HOME='C:\Users\12917\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat testDebugUnitTest assembleDebug
```

Web 招聘方演示页面：

```powershell
cd E:\githubitem\AD\ad-project\web
npm run lint
npm run test -- --run
npm run build
```

## 10. 下一阶段怎么把 HR 推荐做成真实功能

当前 Web 的 Applications 排序明确标为 Demo，不会伪装成线上模型结果。要实现真实 HR 端，应依次完成：

1. 定义“某个职位的可见候选池”，只能包含已投递且招聘方有权限查看的申请人。
2. Spring 从不可变的 `resume_snapshot` 读取候选资料，避免候选人之后修改简历影响历史申请。
3. Spring 调用现有 Python `/internal/v1/recommend/candidates`，绝不让 Web 直接调用。
4. 保存 `job + application resume snapshot + model version` 的推荐快照。
5. 给公开 OpenAPI 增加 recruiter candidate recommendation endpoint。
6. 用真实招聘人员的相关性标注做离线评估和盲测，再决定是否默认开启排序。

这条边界很重要：模型可以帮助人更快看材料，但不能越权读取候选人、不能自动改变申请状态，也不能把分数当作录用结论。
