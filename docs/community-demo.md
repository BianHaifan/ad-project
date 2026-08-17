# Community Demo

Community Demo 是 AD Project 的社区互动演示：Candidate 和 Recruiter 可以围绕职业话题发布纯文本动态、浏览 Feed，并在单条动态中点赞和评论。

## 用户能力

- Candidate：从 Profile 的 Community 入口浏览 Feed、发布动态、查看详情、点赞/取消点赞、浏览评论和发表评论。
- Recruiter：从 Web AppShell 的 Community 入口浏览 Feed、发布动态、查看详情、点赞/取消点赞、浏览评论和发表评论。
- 两种角色读取同一份持久化数据；点赞数、评论数和当前用户点赞状态均以后端响应为准。

## 三端架构

```text
Candidate Android / Recruiter Web
              ↓ HTTPS/HTTP API
          Spring Boot API
              ↓
             MySQL
```

Android 和 Web 通过各自的集中 HTTP Client 调用 Spring Boot；Spring Boot 负责认证、角色授权、正文校验、排序、分页和计数；MySQL 保存动态、点赞和评论。

## Community API

所有接口位于 `/api/v1/community`，仅已登录的 Candidate 和 Recruiter 可使用：

1. `GET /posts`：分页读取 Feed。
2. `POST /posts`：发布纯文本动态。
3. `GET /posts/{postId}`：读取单条动态详情。
4. `PUT /posts/{postId}/like`：幂等点赞。
5. `DELETE /posts/{postId}/like`：幂等取消点赞。
6. `GET /posts/{postId}/comments`：分页读取评论。
7. `POST /posts/{postId}/comments`：发布纯文本评论。

Feed 按 `createdAt DESC, id DESC` 排序；评论按 `createdAt ASC, id ASC` 排序。动态正文为 1–2000 个 Unicode code points，评论正文为 1–500 个 Unicode code points；两者都会移除首尾 Unicode 空白并保留内部空白。

## 第一版不做什么

不包含编辑、删除、回复层级、草稿、图片/附件、通知、搜索、推荐、举报、审核或 Admin 社区管理功能。

## 本地启动与手动验收

启动 MySQL 后，启动 Spring Boot，并让 Recruiter Web 通过 Vite 的 `/api` proxy 访问后端。Android Emulator Debug 使用 `http://10.0.2.2:8081/api/v1/`；其 Debug 专用网络配置只允许该本机 Emulator 地址的 HTTP。

使用一个 Candidate 和一个 Recruiter 登录：任一端发帖，另一端应看见同一条动态；分别点赞、取消点赞和发表评论，另一端刷新详情后应看见后端返回的最新计数和内容。评论分页失败时可使用 Retry comments 重试同一页。
