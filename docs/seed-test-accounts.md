# 推荐系统测试账号

> 生成日期：2026-08-16
> 用途：测试推荐系统（求职者 → 岗位 方向）。
> 说明：以下均为**测试种子账号**，使用 `seed.*` 邮箱前缀与统一测试密码，与真实账号完全隔离。

## 统一密码

所有账号登录密码均为：

```
Test1234!
```

## 招聘者账号（共 3 个，各已发布 50 个岗位）

| 姓名 | 邮箱 | 公司 | 岗位侧重 |
|---|---|---|---|
| Derek Ong | `seed.rec1@example.com` | TechNova Solutions | 后端 / Go / DevOps |
| Mei Ling Tan | `seed.rec2@example.com` | DataSphere Analytics | ML / 数据科学 / 数据工程 |
| Ryan Koh | `seed.rec3@example.com` | PixelWorks Studio | 前端 / 全栈 / 移动端 |

## 求职者账号（共 3 个，简历与偏好各不相同）

| 姓名 | 邮箱 | 技能方向 | 期望最低薪 |
|---|---|---|---|
| Aisha Tan | `seed.cand1@example.com` | Java / Spring 后端 | SGD 7000 / 月 |
| Ben Lim | `seed.cand2@example.com` | Python / ML / 数据 | SGD 8500 / 月 |
| Chloe Ng | `seed.cand3@example.com` | React / TypeScript 前端 | SGD 6000 / 月 |

> 三个求职者技能域相互正交，推荐结果应有明显区分度，便于验证推荐系统的排序质量。

## 数据概况

| 项 | 数量 |
|---|---|
| 岗位（`ACTIVE` + `PUBLIC` + 已发布） | 150（每招聘者 50） |
| 简历 / 个人页 / 求职偏好 | 各 3 |
| 公司 | 3 |

## 登录方式

- **Android 求职端**：用上面的求职者账号登录，进入「Job preferences」→ 推荐页查看 `AI Match %`。
- **Web 招聘端**：用招聘者账号登录，可查看自己公司发布的岗位。

## 备注

- 推荐接口返回 `MODEL` 还是 `FALLBACK` 取决于 ML 服务是否运行；ML 服务未启动时后端自动降级为规则排序。
- 这些是纯测试凭据，请勿把真实账号密码写入本文件。
- 清理测试数据见：`C:\Users\14188\AppData\Local\Temp\adproject_cleanup.sql`
