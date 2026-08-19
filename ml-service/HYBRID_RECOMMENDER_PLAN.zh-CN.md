# Embedding、Collaborative Filtering 与 Hybrid 推荐训练计划

## 1. 当前结论

本轮先实现了可重复的离线研究基线，没有替换线上 `match-hgb-retrieval-v4`。

实验使用：

- 738,900 个非冻结 Candidate–Job Pair；
- 2,463 个训练 Candidate Query；
- 20 个冻结 Query、6,000 个冻结 Pair；
- 100 个确定性选择的开发 Query；
- 20,083 个参与实验的 Job；
- 158,873 个由 AI Teacher 相关性合成的隐式交互。

| 协议 | 方法 | MAP@10 | NDCG@10 | Precision@5 |
|---|---:|---:|---:|---:|
| 冷启动冻结 Teacher Audit | 当前 v4 | 0.991667 | 0.984582 | 0.970000 |
| 冷启动冻结 Teacher Audit | LSA Embedding | 0.361046 | 0.608634 | 0.500000 |
| 冷启动冻结 Teacher Audit | 自动调权 Hybrid | 0.991667 | 0.984582 | 0.970000 |
| 合成暖启动 Holdout | 当前 v4 | 0.946744 | 0.958097 | 0.920000 |
| 合成暖启动 Holdout | Collaborative SVD | 0.634249 | 0.802147 | 0.700000 |
| 合成暖启动 Holdout | LSA Embedding | 0.173272 | 0.536676 | 0.240000 |
| 合成暖启动 Holdout | Popularity | 0.109254 | 0.482867 | 0.180000 |
| 合成暖启动 Holdout | 自动调权 Hybrid | 0.946744 | 0.958097 | 0.920000 |

自动调参得到：

- 冷启动：`ranker=1.0, embedding=0.0`；
- 暖启动：`ranker=1.0, embedding=0.0, cf=0.0`。

因此当前正确决定是：保留 v4 作为 Active Ranker；CF 继续停留在实验阶段；神经 Embedding
不直接混合进最终分数，但可以继续作为第二路召回候选。

机器可读结果：

- `reports/hybrid_experiment_v1.json`：LSA、CF 和基础 Hybrid；
- `reports/neural_embedding_frozen_v1.json`：神经 Embedding 直接排序与固定权重混合；
- `reports/neural_retrieval_full_catalog_v1.json`：全职位目录神经召回；
- `reports/hybrid_sensitivity_*.json`：阈值、维度与随机种子敏感性；
- `reports/hybrid_experiment_summary_v2.json`：汇总结论。

## 1.1 后续神经 Embedding 与稳定性实验

通用 `all-MiniLM-L6-v2` 在 20 个冻结 Query 上直接排序时：

| 方法 | MAP@10 | NDCG@10 | Precision@5 |
|---|---:|---:|---:|
| 当前 v4 | 0.991667 | 0.984582 | 0.970000 |
| 神经 Embedding | 0.347782 | 0.609405 | 0.470000 |
| 90% v4 + 10% 神经 Embedding | 0.964292 | 0.954150 | 0.960000 |

固定加入 10%～50% 神经 Embedding 均导致指标下降，因此不采用直接分数融合。

将神经 Embedding 改为全目录第二路召回后：

- Job Catalog：25,298；
- 每个 Candidate 召回 300 个 Job；
- 与现有召回平均只重合 54.05 个；
- 平均带来 245.95 个新 Job；
- 现有召回池含 1,839 个 Teacher 相关 Pair；
- 神经召回池含 1,634 个 Teacher 相关 Pair；
- 并集含 2,996 个 Teacher 相关 Pair，比原池增加 1,157 个，约增加 62.9%；
- 神经召回后由 v4 重排的 MAP@10 为 0.985714；
- 并集后由 v4 重排的 MAP@10 为 0.990000。

这说明 Embedding 更适合“补充候选 Job”，而不是替代业务特征 Ranker。

严格 CF 配置使用 Seed 7、42、99 做了稳定性复验，只有 Seed 42 选择了 10% CF，另外两个 Seed
都选择 0%。因此 10% CF 的单次提升不可复现，不作为上线证据。

## 2. 为什么这次没有提升

1. 冻结标签仍由同一个 AI Teacher 产生，v4 的目标就是模仿该 Teacher，因此该测试天然偏向 v4。
2. LSA 将文本压缩为 128 维稠密向量，会丢失地点、工作模式、经验、技能零命中和领域冲突等业务约束。
3. 当前没有真实行为日志。CF 使用的“交互”由 `relevance >= 2` 合成，本质上仍然来自内容标签，没有增加新的用户兴趣信号。
4. 人工标注文件的 `human_relevance_0_to_3` 仍为空，不能声称完成了独立人工盲测。

这次结果只能证明 Embedding/CF 代码可以训练和评估，不能证明其对真实用户有效。

## 3. 第一优先级：采集真实行为

Spring Boot 应成为唯一行为采集入口。建议增加 `recommendation_interactions`，至少记录：

| 字段 | 作用 |
|---|---|
| `candidate_id`、`job_id` | User–Item Pair |
| `event_type` | `IMPRESSION/CLICK/SAVE/APPLY/DISMISS/INTERVIEW/OFFER/HIRE` |
| `recommendation_run_id` | 追踪事件来自哪次推荐 |
| `model_version` | 追踪展示时所用模型 |
| `position` | 判断位置偏差 |
| `source` | 推荐、搜索或普通列表 |
| `occurred_at` | UTC 时间切分与时序分析 |

必须先记录 `IMPRESSION`。没有曝光记录时，“没有点击”既可能表示不喜欢，也可能表示用户根本没有看见，不能作为负样本。

现有 `candidate_saved_jobs` 和 `applications` 可以作为正反馈起点，但不能替代完整曝光日志。事件权重只作为初始假设，例如 Click < Save < Apply < Interview < Offer，必须通过验证集调参，不能硬编码成业务事实。

## 4. Embedding 训练路线

### 阶段 E1：当前 LSA 基线

- TF-IDF 后使用 `TruncatedSVD` 得到 128 维稠密向量；
- 不需要 PyTorch，CPU 可复现；
- 作用是验证 Embedding 接口、相似度计算、融合和评估流程；
- 当前结果不支持上线。

### 阶段 E2：预训练神经 Sentence Embedding

- 选择许可证允许、英文招聘文本效果可验证的小型 Sentence Transformer；
- Candidate 文本使用 Headline、Resume、Skills 和 Desired Titles；
- Job 文本使用 Title、Description、Requirements 和 Skills；
- 向量离线生成，模型服务启动时加载 Job 向量索引；
- 使用余弦相似度召回 Top 100～300，再由 v4 重排；
- 首轮不微调，先与 LSA、TF-IDF 召回率比较。

当前已经用 `sentence-transformers/all-MiniLM-L6-v2` 完成第一轮 CPU 实验。模型输出 384 维
向量，默认最多处理 256 word pieces，所以输入将 Title、Desired Role 和 Skills 放在最前面，
再拼接 Resume/Description 摘要。模型只安装在本地实验环境，尚未写入默认 `environment.yml`。

本地可选安装：

```powershell
conda run -n ad-project-ml python -m pip install sentence-transformers==6.0.0
```

正式加入团队依赖前必须解决 CPU/GPU 安装差异、模型缓存、许可证记录、离线部署和依赖体积问题。

### 阶段 E3：领域对比学习

- Positive：真实 Save、Apply、Interview、Offer/Hire Pair；
- Hard Negative：已经曝光但忽略/明确 Dismiss，以及同领域但关键技能不匹配的 Job；
- Candidate 与 Job 使用 Two-Tower 编码；
- 训练目标使用 Contrastive Loss 或 Multiple Negatives Ranking Loss；
- Candidate 级切分，禁止同一 Candidate 的未来事件泄漏到训练集。

## 5. Collaborative Filtering 训练路线

### 阶段 C1：研究基线

当前 `CollaborativeSvd` 使用 scikit-learn `TruncatedSVD` 分解 Candidate–Job 隐式反馈矩阵，只用于验证训练和评估管线。

### 阶段 C2：真实隐式反馈模型

积累真实事件后，比较：

- Popularity：必须保留的最低基线；
- Implicit ALS：适合稀疏隐式反馈；
- BPR：直接优化正样本排在负样本之前；
- LightFM/混合矩阵分解：可同时使用用户和职位内容，减轻冷启动。

第一次可信 CF 实验至少要求：

- 有足够多的 Candidate，而不是单个测试账号；
- 每个参与暖启动评估的 Candidate 至少有 5 个有意义事件；
- 至少保留 1～2 个未来正反馈作为测试；
- 负样本只从已经曝光的 Job 中采样；
- 使用按时间切分，而不是随机打散未来行为。

如果条件不满足，应继续使用内容模型，不强行上线 CF。

## 6. Hybrid 推荐结构

建议最终分成三层：

```text
后端权限、状态和可见性过滤
        ↓
多路召回
  ├─ 关键词/结构化召回
  ├─ 神经 Embedding 向量召回
  └─ Collaborative Filtering 召回
        ↓
候选集合去重
        ↓
监督式 Ranker 重排
        ↓
技能零命中、领域冲突等 Guard
        ↓
Top-N、推荐理由、模型版本
```

新用户或新 Job 没有行为时，CF 权重必须自动归零，回退到内容和 Embedding。用户历史足够时，再让 CF 参与召回或重排。

根据当前实验，更具体的候选实现为：

```text
现有结构化召回 Top 300 ─┐
                          ├─ 去重并集 → v4 Ranker → Guard → Top-N
神经 Embedding 召回 Top 300 ┘
```

Job Embedding 应在 Job 发布或更新时增量生成并持久化，不得在用户请求期间重新编码整个职位目录。
Candidate Embedding 可按简历和偏好版本缓存。当前 CPU 全目录 25,298 个 Job 编码约需 263 秒，
证明在线全量重编码不可接受。

不要直接手写永久固定权重。应在开发集调权，在时间隔离测试集只评估一次，并记录每个版本的配置。

## 7. 统一评估协议

每次模型更新至少报告：

1. 冷启动 Candidate：MAP@10、NDCG@10、Recall@10；
2. 暖启动 Candidate：对未来行为的 HitRate@10、Recall@10、NDCG@10；
3. 新 Job：发布后无行为时的召回覆盖；
4. Embedding 召回：Recall@100/300；
5. 多样性、Coverage 和热门职位偏差；
6. P50/P95 推理延迟和模型内存；
7. 人工盲测结果；
8. Counterfactual 技能、领域和 Seniority 安全测试。

上线门槛：Hybrid 必须在独立测试集上超过 v4，而不是只在训练集、Teacher 标签或调参集上提升；关键 Guard 不得退化；P95 延迟必须满足服务预算。

## 8. 复现实验

在当前 PR 工作树执行：

```powershell
cd E:\githubitem\AD\ad-project-admin-ml-pr\ml-service
$env:PYTHONPATH=(Resolve-Path .\src).Path

conda run -n ad-project-ml python scripts\experiment_hybrid_recommender.py `
  --model artifacts\active\model.joblib `
  --teacher-model E:\githubitem\AD\ad-project\ml-service\artifacts\teacher\prelabeler-distilled-v2.joblib `
  --retrieval-pairs E:\githubitem\AD\ad-project\ml-service\data\processed\training_pairs_retrieval_top300_v4.csv `
  --training-pairs E:\githubitem\AD\ad-project\ml-service\data\processed\training_pairs_retrieval_pseudo_v4.csv `
  --query-ids E:\githubitem\AD\ad-project\ml-service\data\processed\frozen_blind_query_ids.txt `
  --report reports\hybrid_experiment_v1.json
```

检查代码：

```powershell
conda run -n ad-project-ml ruff check `
  src\ad_recommender\hybrid.py `
  scripts\experiment_hybrid_recommender.py `
  tests\test_hybrid.py

conda run -n ad-project-ml pytest tests\test_hybrid.py -q
```

神经 Embedding 冻结重排：

```powershell
conda run -n ad-project-ml python scripts\experiment_neural_embedding.py `
  --model artifacts\active\model.joblib `
  --teacher-model E:\githubitem\AD\ad-project\ml-service\artifacts\teacher\prelabeler-distilled-v2.joblib `
  --retrieval-pairs E:\githubitem\AD\ad-project\ml-service\data\processed\training_pairs_retrieval_top300_v4.csv `
  --query-ids E:\githubitem\AD\ad-project\ml-service\data\processed\frozen_blind_query_ids.txt `
  --report reports\neural_embedding_frozen_v1.json
```

神经 Embedding 全目录召回：

```powershell
conda run -n ad-project-ml python scripts\experiment_neural_retrieval.py `
  --model artifacts\active\model.joblib `
  --teacher-model E:\githubitem\AD\ad-project\ml-service\artifacts\teacher\prelabeler-distilled-v2.joblib `
  --retrieval-pairs E:\githubitem\AD\ad-project\ml-service\data\processed\training_pairs_retrieval_top300_v4.csv `
  --query-ids E:\githubitem\AD\ad-project\ml-service\data\processed\frozen_blind_query_ids.txt `
  --report reports\neural_retrieval_full_catalog_v1.json
```

原始数据和训练 Pair 继续遵守 `.gitignore` 与许可证限制，不提交 Git。实验代码、文档、测试以及不包含个人原文的聚合报告可以提交。

## 9. 已接入真实推理服务的混合运行时

当前 `artifacts/active` 已升级为 `match-hybrid-lsa-cf-v1`，真实请求会执行：

```text
v4 Guarded HGB Ranker（85%）
          +
64 维 TF-IDF + TruncatedSVD LSA Embedding（10%）
          +
32 维隐式反馈 TruncatedSVD Collaborative Filtering（5%）
          ↓
混合分数、排序、原有解释和 Guard
```

CF 使用 284 个暖 Candidate、1,500 个暖 Job 和 40,439 条由 Teacher relevance 派生的伪隐式反馈训练。
对于刚注册、没有历史的真实用户，服务先通过 LSA Embedding 找到最相似的暖 Candidate；新职位同样映射
到最相似暖 Job，再读取 CF 潜向量分数。内部响应的 `component_modes` 会标记
`EMBEDDING_BRIDGED`，不能把它描述成该新用户已经产生真实点击或申请历史。

真实 Spring Boot + MySQL + Python ML 测试返回：

- `modelVersion=match-hybrid-lsa-cf-v1`；
- `featureVersion=pair-features-v1+prelabel-features-v3+lsa64+cf32`；
- ML Engineer 对 ML Candidate 为 100 分、Rank 1；
- Retail Sales Associate 为 0 分、Rank 2；
- 端到端 ML 推理约 24 ms；
- 直接 ML 响应同时包含 ranker、embedding cosine、CF latent 和 hybrid final 分数。

这一实现适合演示“模型已进入真实服务链路”，但不代表 CF 已具有生产有效性。汇报必须说明 CF 训练信号仍是
伪交互，尚未使用真实 impression、click、save、apply、interview 或 hire 日志。积累真实行为后，应按时间切分
重新训练 CF，并重新调节权重；在独立盲测超过 v4 前不能声称推荐准确率得到提升。
