# ML 推荐功能更新说明与逐步测试手册

更新日期：2026-08-13
适用分支：`agent/admin-system`

本文只说明本次“求职者职位推荐 + 招聘方候选人匹配基础”更新，并提供适合第一次接触项目的本地测试流程。更深入的模型原理和代码阅读路线见 [ML_RECOMMENDATION_GUIDE.zh-CN.md](ML_RECOMMENDATION_GUIDE.zh-CN.md)。

---

## 1. 这次更新解决了什么问题

在更新前，Candidate 可以浏览职位，但系统没有根据简历和求职偏好真正计算个性化排序；Web 招聘方页面中的匹配分数也只是 mock 数据。

更新后已经完成一条真实的 Candidate 推荐链路：

```text
Candidate Android App
        |
        | JWT + public REST API
        v
Spring Boot :8080
        |-- 从 MySQL 读取简历、求职偏好、有效职位
        |-- 检查 Candidate 权限
        |-- 调用私有 ML 服务
        v
FastAPI :8000
        |-- 提取匹配特征
        |-- 调用 scikit-learn 模型
        |-- 返回 Top-N、分数和解释
        v
Spring Boot
        |-- 保存带版本的推荐快照
        |-- 返回 Android
        v
Recommended jobs 页面
```

如果 Python ML 服务停止，Spring Boot 不会让职位页面整体报错，而是切换到确定性的规则排序，并在响应中标记：

```json
{
  "source": "FALLBACK",
  "modelStatus": "DEGRADED"
}
```

## 2. 本次实现范围

### 2.1 Python / scikit-learn

- 创建 Conda 环境 `ad-project-ml`，使用 Python 3.12。
- 使用 scikit-learn 1.9.0 训练匹配模型。
- 建立可重复的数据下载、清洗、训练和评估命令。
- 提供私有 FastAPI：
  - `GET /internal/v1/health`
  - `POST /internal/v1/recommend/jobs`
  - `POST /internal/v1/recommend/candidates`
- 两个推荐 POST 接口必须带 `X-Internal-Token`。
- Python 不读取 MySQL、不验证客户端 JWT，也不直接暴露给 Android/Web。

主要文件：

- `ml-service/src/ad_recommender/data.py`
- `ml-service/src/ad_recommender/features.py`
- `ml-service/src/ad_recommender/model.py`
- `ml-service/src/ad_recommender/evaluation.py`
- `ml-service/src/ad_recommender/api.py`

### 2.2 Spring Boot

新增公开 Candidate API：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/candidate/job-preferences` | 读取当前求职偏好 |
| PUT | `/api/v1/candidate/job-preferences` | 保存求职偏好 |
| GET | `/api/v1/candidate/recommendations/jobs?limit=20` | 获取 Top-N 推荐职位 |

后端负责：

- 只允许 Candidate 使用上述 API。
- 从数据库生成候选职位集合。
- 只发送推荐所需的最少字段给 Python。
- 模型调用失败时生成规则降级结果。
- 保存推荐分数、模型版本、简历版本、偏好版本、职位版本。
- 简历、偏好或职位变化后，不再把旧分数当作当前结果。
- 每次新推荐替换上一轮 Top-N，避免掉出榜单的职位继续显示旧分数。

### 2.3 MySQL / Flyway

新增 `V13__create_candidate_recommendations.sql`：

- 为简历及申请简历快照增加 `skills_json`。
- 新增 `candidate_job_preferences`。
- 新增 `candidate_job_recommendations`。

数据库只能通过 Flyway 更新，不需要在 Workbench 手动创建这些表。

### 2.4 Android Candidate

- 简历增加 Skills 输入。
- Profile 增加 `Job preferences` 入口。
- 偏好支持职位名称、地点、workplace type、employment type 和最低月薪。
- Jobs 默认显示 `Recommended`，也可以切换到普通 `Browse`。
- 推荐卡片显示排名和匹配百分比。
- 职位详情显示 strong matches 和 gaps。
- 模型不可用时仍显示规则推荐来源。

Android 模拟器默认访问：

```text
http://10.0.2.2:8080/api/v1/
```

`10.0.2.2` 代表运行模拟器的这台 Windows 电脑，不能在 Android 模拟器中使用 `localhost:8080`。

### 2.5 Recruiter Web

Applications 和 Application Detail 增加匹配排序及解释的演示状态，并明确显示 `Demo`。

当前 Recruiter 端还没有真实的申请查询后端，所以这一部分不能伪装成真实模型结果。Python 已经具备 `/recommend/candidates`，下一阶段需要先完成 Recruiter Applications API，再把申请时保存的 `resume_snapshot` 接入模型。

## 3. 数据与模型结果

本机数据规模：

- 简历：2,483 条。
- 职位：25,298 条。
- 原始弱监督配对：74,170 对。
- 冻结盲测：20 个 Candidate 组、597 对，整组排除。
- 全职位 Top-300 召回：744,900 对。
- 最终AI教师连续伪标签训练对：738,900 对。
- 2/3 分正样本：212,559 对；训练组正样本覆盖率 99.35%。

当前离线验证结果：

| 指标 | 规则基线 | 训练模型 |
|---|---:|---:|
| Precision@5 | 0.5254 | 0.9566 |
| Recall@10 | 0.0811 | 0.2616 |
| NDCG@10 | 0.6244 | 0.9761 |
| MAP@10（全部查询） | 0.3998 | 0.9674 |
| MAP@10（存在正样本） | 0.4039 | 0.9773 |
| 正样本查询覆盖率 | 0.9899 | 0.9899 |

这些结果说明最终模型能够拟合AI教师伪标签，但不能直接解释为真实录用效果。人工复核表的人工标签列目前仍为空，因此独立盲测尚未完成。模型只能辅助排序，不能自动录用或拒绝 Candidate。

另外已对 20 个从学生训练中完整排除的 Candidate 组执行教师一致性审计：训练重叠组为 0，新 Top-300 召回共 6,000 对，MAP@10 为 0.9917，正样本查询覆盖率为 1.0。该结果不是人工盲测，报告位于 `ml-service/reports/frozen_teacher_audit_v4.json`。

原始 CSV、处理后的训练对、人工标注、教师模型和历史模型已被 `.gitignore` 排除；约 1.9 MB 的 `artifacts/active/model.joblib` 与 manifest 允许提交，确保队友 clone 后可以直接运行推理。

---

# 4. 初学者真实测试：一次只完成一个检查点

下面使用四个 PowerShell 窗口：

| 窗口 | 用途 | 是否一直保持打开 |
|---|---|---|
| A | MySQL | 是 |
| B | Python ML | 是 |
| C | Spring Boot | 是 |
| D | 输入测试请求 | 可以反复使用 |

如果某个服务已经启动，不要再次启动相同端口。

## 检查点 0：确认项目目录

在一个新的 PowerShell 中执行：

```powershell
cd E:\githubitem\AD\ad-project
git branch --show-current
```

预期：

```text
agent/admin-system
```

如果不是这个分支，先停止，不要自行执行 `git switch`，确认当前修改属于哪个分支后再处理。

## 检查点 1：检查端口是否已经启动

```powershell
$ports = 3306, 8000, 8080, 4173
foreach ($port in $ports) {
  $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
  [pscustomobject]@{
    Port = $port
    Listening = [bool]$listener
  }
}
```

端口含义：

- `3306`：MySQL。
- `8000`：Python ML。
- `8080`：Spring Boot。
- `4173`：React Web，仅在测试 Web 时需要。

如果 `3306/8000/8080` 都是 `True`，直接跳到检查点 5。

## 检查点 2：启动 MySQL

打开 MySQL Workbench，连接：

```text
Hostname: localhost
Port: 3306
Username: root 或你已经创建的本地用户
Default Schema: adproject
```

连接成功后执行：

```sql
SELECT 1;
```

预期结果只有一行 `1`。

不要在文档、截图或 Git 文件里保存数据库密码。

## 检查点 3：启动 Python ML 服务

打开 PowerShell 窗口 B：

```powershell
cd E:\githubitem\AD\ad-project\ml-service

$env:ML_INTERNAL_TOKEN = Read-Host '输入一个只用于本地的 ML 共享令牌'
$env:ML_MODEL_PATH = 'artifacts\active\model.joblib'

conda run -n ad-project-ml ad-recommender serve --port 8000
```

注意：

- 记住刚输入的 ML 共享令牌，检查点 4 还要输入同一个值。
- 不要关闭窗口 B。
- `conda run` 可以避免新终端里 `conda activate` 没生效的问题。

预期看到：

```text
Uvicorn running on http://127.0.0.1:8000
```

另开一个临时 PowerShell 验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8000/internal/v1/health
```

预期：

```text
status          : ready
model_version   : match-hgb-retrieval-v4
feature_version : pair-features-v1+prelabel-features-v2
```

如果是 `not_ready`，检查文件是否存在：

```powershell
Test-Path E:\githubitem\AD\ad-project\ml-service\artifacts\active\model.joblib
```

应该返回 `True`。

## 检查点 4：启动 Spring Boot

打开 PowerShell 窗口 C：

```powershell
cd E:\githubitem\AD\ad-project\backend

$env:DB_URL = 'jdbc:mysql://localhost:3306/adproject?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:DB_USERNAME = 'adproject'

$secureDbPassword = Read-Host '输入本地 MySQL 密码' -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', $secureDbPassword).Password

$jwtBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)

$env:ML_ENABLED = 'true'
$env:ML_BASE_URL = 'http://127.0.0.1:8000'
$env:ML_INTERNAL_TOKEN = Read-Host '再次输入窗口 B 使用的同一个 ML 共享令牌'
$env:SERVER_PORT = '8080'

$mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.11" `
  -Recurse -Filter mvn.cmd | Select-Object -First 1 -ExpandProperty FullName

& $mvn spring-boot:run
```

不要关闭窗口 C。

预期日志包含：

```text
Successfully applied ... migration
Started BackendApplication
```

如果数据库已经升级过，Flyway 会显示当前为 V7，而不是重复建表。

如果 `$mvn` 为空，可以在 IntelliJ 中打开：

```text
backend/src/main/java/com/adproject/BackendApplication.java
```

点击类旁边的绿色运行按钮；但必须在 Run Configuration 中设置上面相同的环境变量。

## 检查点 5：创建一个全新的 Candidate 测试账号

打开 PowerShell 窗口 D。这个窗口后面不要关闭，因为 `$session`、`$auth` 都只保存在当前 PowerShell 进程中。

```powershell
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$testEmail = "ml-test-$stamp@example.com"

$secureTestPassword = Read-Host '给测试账号设置至少 8 位密码' -AsSecureString
$testPassword = [Net.NetworkCredential]::new('', $secureTestPassword).Password

$registerBody = @{
  role = 'CANDIDATE'
  fullName = 'ML Test Candidate'
  email = $testEmail
  password = $testPassword
  acceptedTermsVersion = '2026-08'
} | ConvertTo-Json

$session = Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/v1/auth/register' `
  -ContentType 'application/json' `
  -Body $registerBody

$auth = @{
  Authorization = "Bearer $($session.data.accessToken)"
}

$session.data.user | Select-Object userId, role, email
```

预期：

```text
role  : CANDIDATE
email : ml-test-...@example.com
```

记住 `$testEmail` 和你刚输入的密码，稍后 Android 登录会使用。

为什么新开终端后 `$session` 会消失：它只是 PowerShell 当前进程的内存变量。浏览器或 Android 能保持登录，是因为客户端把 token 保存到自己的 session storage/DataStore。

## 检查点 6：确认数据库里有可推荐职位

仍在窗口 D：

```powershell
$jobs = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/jobs?page=1&pageSize=10' `
  -Headers $auth

$jobs.meta
$jobs.data | Select-Object jobId, title, location, status
```

必须满足：

- `meta.total` 大于 0。
- 至少一个职位状态为 `ACTIVE`。
- 职位所属公司已经 `APPROVED`。

如果 total 为 0，需要先用 Recruiter 创建职位，并由 Admin 批准公司后发布职位，不能直接在数据库中伪造公开职位。

## 检查点 7：创建带 Skills 的简历

```powershell
$resumeBody = @{
  fullName = 'ML Test Candidate'
  age = 26
  location = 'Singapore'
  headline = 'Backend Software Engineer'
  summary = 'Builds Java Spring Boot APIs, Python machine learning services, and MySQL systems.'
  skills = @('Java', 'Spring Boot', 'Python', 'SQL', 'REST API')
  experiences = @()
  expectedVersion = 0
} | ConvertTo-Json -Depth 6

$resume = Invoke-RestMethod `
  -Method Put `
  -Uri 'http://localhost:8080/api/v1/candidate/resume' `
  -Headers $auth `
  -ContentType 'application/json' `
  -Body $resumeBody

$resume.data | Select-Object resumeId, headline, skills, version
```

预期：

- `version` 为 `1`。
- `skills` 包含 Java、Spring Boot、Python、SQL 和 REST API。

`expectedVersion=0` 只用于第一次创建。修改现有简历时必须传当前版本，否则返回 `409 VERSION_CONFLICT`。

## 检查点 8：保存求职偏好

```powershell
$preferenceBody = @{
  desiredTitles = @('Backend Engineer', 'Software Engineer')
  preferredLocations = @('Singapore')
  workplaceTypes = @('HYBRID', 'REMOTE')
  employmentTypes = @('FULL_TIME')
  minimumSalary = 4000
  salaryCurrency = 'SGD'
  salaryPeriod = 'MONTH'
  expectedVersion = 0
} | ConvertTo-Json -Depth 6

$preference = Invoke-RestMethod `
  -Method Put `
  -Uri 'http://localhost:8080/api/v1/candidate/job-preferences' `
  -Headers $auth `
  -ContentType 'application/json' `
  -Body $preferenceBody

$preference.data
```

预期 `version` 为 `1`。

## 检查点 9：第一次请求真实模型推荐

```powershell
$watch = [Diagnostics.Stopwatch]::StartNew()

$recommendation = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/candidate/recommendations/jobs?limit=10' `
  -Headers $auth

$watch.Stop()

$recommendation.meta
$recommendation.data | Format-Table rank, title, companyName, matchScore
"HTTP round trip: $($watch.ElapsedMilliseconds) ms"
```

正常结果：

- `meta.source` 为 `MODEL`。
- `meta.modelStatus` 为 `ACTIVE`。
- `meta.modelVersion` 为 `match-hgb-retrieval-v4`。
- `data` 按 `rank` 从 1 开始排列。
- `matchScore` 在 0 到 100 之间。

第一次请求可能需要几秒，因为模型需要建立特征缓存；同一输入第二次请求通常明显更快：

```powershell
$warmWatch = [Diagnostics.Stopwatch]::StartNew()
$warmResult = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/candidate/recommendations/jobs?limit=10' `
  -Headers $auth
$warmWatch.Stop()

"Warm request: $($warmWatch.ElapsedMilliseconds) ms"
```

## 检查点 10：查看推荐解释和职位详情快照

```powershell
$top = $recommendation.data[0]

$top | Select-Object rank, title, matchScore
$top.matchAnalysis

$detail = Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/jobs/$($top.jobId)" `
  -Headers $auth

$detail.data | Select-Object jobId, title, matchScore, matchAnalysis
```

预期：

- `strongMatches` 说明匹配到的技能、地点或工作方式。
- `gaps` 说明需要人工进一步确认的差距。
- 职位详情中的 `matchScore` 与当前推荐快照一致。

这些解释是辅助信息，不代表录用结论。

## 检查点 11：验证 ML 故障降级

1. 回到窗口 B。
2. 按 `Ctrl + C` 停止 Python。
3. 不要停止 MySQL 和 Spring Boot。
4. 回到窗口 D 执行：

```powershell
$fallback = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/candidate/recommendations/jobs?limit=10' `
  -Headers $auth

$fallback.meta
$fallback.data | Format-Table rank, title, matchScore
```

预期：

```text
source       : FALLBACK
modelStatus  : DEGRADED
modelVersion : fallback-rules-v1
```

这说明 Python 停止不会破坏职位浏览。

完成后回到窗口 B，重新执行检查点 3 的启动命令，再请求一次推荐，确认恢复为：

```text
source      : MODEL
modelStatus : ACTIVE
```

## 检查点 12：验证并发版本冲突

第一次保存偏好后版本已经是 1。故意重复发送旧的 `expectedVersion=0`：

```powershell
try {
  Invoke-RestMethod `
    -Method Put `
    -Uri 'http://localhost:8080/api/v1/candidate/job-preferences' `
    -Headers $auth `
    -ContentType 'application/json' `
    -Body $preferenceBody
} catch {
  $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
  $errorBody.error
}
```

预期错误码：

```text
VERSION_CONFLICT
```

这是为了防止两个页面用旧数据互相覆盖。

## 检查点 13：验证未登录不能访问

```powershell
try {
  Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/v1/candidate/recommendations/jobs'
} catch {
  [int]$_.Exception.Response.StatusCode
}
```

预期返回：

```text
401
```

不要把 `$session.data.accessToken` 复制到文档、截图或 GitHub。

---

# 5. 在 Android 模拟器里真实点击测试

完成 PowerShell API 测试后再进行 UI 测试，这样如果页面有问题，可以确定 MySQL、Spring 和模型已经正常。

## Android 步骤 1：确认服务

确保：

- MySQL `3306` 正在运行。
- Python `8000` 正在运行。
- Spring Boot `8080` 正在运行。

## Android 步骤 2：打开工程

用 Android Studio 打开：

```text
E:\githubitem\AD\ad-project\android
```

等待 Gradle Sync 完成。

## Android 步骤 3：启动模拟器

1. 打开 Device Manager。
2. 启动一个 API 35 模拟器。
3. 选择 `app` 运行配置。
4. 点击绿色 Run。

Debug 包默认已经使用 `10.0.2.2:8080`，不需要改成 Windows 的局域网 IP。

## Android 步骤 4：登录

使用检查点 5 中创建的：

- `$testEmail` 对应的邮箱。
- 你在 `Read-Host` 中输入的测试密码。

## Android 步骤 5：检查简历

1. 点击底部 `Me`。
2. 点击 `Online resume`。
3. 确认 Skills 中包含 Java、Spring Boot、Python、SQL、REST API。
4. 修改一个字段并保存。
5. 看到成功反馈后返回。

修改简历后，旧推荐快照会失效，这是正确行为。

## Android 步骤 6：检查偏好

1. 点击底部 `Me`。
2. 点击 `Job preferences`。
3. 确认 Desired titles、Locations、Workplace、Employment、Salary 与 API 测试输入一致。
4. 修改最低薪资并保存。
5. 确认按钮在提交时不可重复点击，并出现保存成功反馈。

## Android 步骤 7：检查推荐列表

1. 回到 Jobs。
2. 点击顶部 `Recommended`。
3. 确认显示 `Recommended for you`。
4. 确认职位卡片显示匹配百分比和推荐排名。
5. 点击一个职位进入详情。
6. 确认显示 `AI Match Analysis`、Strong 和 Gap。
7. 返回后点击 `Browse`，确认可以切换回普通职位列表。

## Android 步骤 8：验证 UI 降级

1. 停止 Python 服务，但保留后端。
2. 回到 Jobs → Recommended，重新刷新。
3. 页面仍应有职位，而不是整体崩溃。
4. 推荐来源应显示 fallback/degraded 的含义。
5. 重新启动 Python 后再刷新，恢复模型推荐。

---

# 6. Recruiter Web 演示测试

Web 当前只演示候选人排序交互，不调用真实候选人推荐接口。

启动：

```powershell
cd E:\githubitem\AD\ad-project\web
npm run dev
```

打开：

```text
http://localhost:4173
```

测试 Applications：

1. 使用 Recruiter 登录。
2. 打开 Applications。
3. 默认启用 `AI ranked · Demo`。
4. 检查候选人按 mock `matchScore` 从高到低排列。
5. 关闭 AI ranked，确认恢复普通顺序。
6. 打开 Application Detail，确认匹配卡片明确标为 Demo。

验收重点：页面不能让用户误以为 Demo 分数来自线上模型。

---

# 7. 自动化测试命令

## 7.1 Python

```powershell
cd E:\githubitem\AD\ad-project\ml-service
conda run -n ad-project-ml ruff check .
conda run -n ad-project-ml pytest -q
```

当前预期：18 项测试通过；可能出现 FastAPI TestClient 的依赖弃用 warning，不影响测试结果。

## 7.2 Spring Boot

```powershell
cd E:\githubitem\AD\ad-project\backend
$mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.11" `
  -Recurse -Filter mvn.cmd | Select-Object -First 1 -ExpandProperty FullName
& $mvn test
```

当前预期：60 项，0 failures、0 errors；没有运行 Docker 时有 2 项 MySQL Testcontainers 测试跳过。真实 MySQL 的 V13 migration 已通过人工 HTTP 测试验证。

## 7.3 Web

```powershell
cd E:\githubitem\AD\ad-project\web
npm run lint
npm run typecheck
npm test
npm run build
```

当前预期：70 项测试通过，生产构建成功。

## 7.4 Android

```powershell
cd E:\githubitem\AD\ad-project\android

$env:JAVA_HOME = 'C:\Users\12917\.jdks\ms-21.0.11'
$env:ANDROID_HOME = 'C:\Users\12917\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

.\gradlew.bat testDebugUnitTest assembleDebug
```

当前预期：30 项测试通过，并生成：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

---

# 8. 常见错误对照

| 现象 | 最常见原因 | 处理方式 |
|---|---|---|
| `Address already in use` | 相同端口已经启动 | 用检查点 1 查看端口，不要重复启动 |
| ML health 是 `not_ready` | 模型路径错误或还没训练 | 检查 `artifacts/active/model.joblib` |
| 推荐返回 `FALLBACK` | Python 未启动、超时或令牌不一致 | 检查 8000 和两个窗口的 `ML_INTERNAL_TOKEN` |
| `401 UNAUTHORIZED` | access token 缺失/过期 | 在当前终端重新登录，重建 `$auth` |
| `403 FORBIDDEN` | 使用了 Recruiter/Admin 调 Candidate API | 换 Candidate 账号 |
| `RESUME_REQUIRED` | Candidate 没有简历 | 先完成检查点 7 |
| `VERSION_CONFLICT` | 使用了旧 `expectedVersion` | GET 最新数据，再使用最新 version |
| 推荐结果为空 | 没有有效公开职位 | 检查 APPROVED 公司及 ACTIVE/PUBLIC 职位 |
| Android 连接失败 | 模拟器使用了 localhost | Debug URL 应为 `10.0.2.2:8080` |
| MySQL access denied | 用户名/密码/授权不一致 | 回 Workbench 验证同一账号可以连接 `adproject` |

## 9. 本次已验证结果

- Python：Ruff 通过，18 项测试通过。
- Spring Boot：60 项、0 失败；2 项因 Docker 未运行跳过。
- Web：lint/typecheck/build 通过，70 项测试通过。
- Android：30 项测试通过，Debug APK 构建成功。
- OpenAPI YAML 可以解析，新增偏好请求禁止多余字段。
- 真实 MySQL + Spring + FastAPI：模型推荐成功。
- 模型冷请求约 3.6 秒；热请求 HTTP 约 38 ms、模型内部约 6 ms。
- 停止 Python 后成功返回 `FALLBACK/DEGRADED`；重启后恢复 `MODEL/ACTIVE`。

## 10. 已知边界与下一步

- Candidate 职位推荐已是真实全链路。
- Recruiter 候选人排序目前只有 Python 内部能力和 Web Demo，尚未接入真实 Recruiter Applications API。
- 当前训练标签来自AI教师模型，需要继续完成人工标注和冻结盲测。
- 模型分数不能自动触发录用、拒绝或申请状态变化。
- 下一步最小工作是实现 Recruiter Applications 查询及权限，然后使用申请时保存的简历快照调用 `/internal/v1/recommend/candidates`。
