# Community Checkpoint B Manual Test

本次双端手动测试结果：

- Candidate Android 成功发布动态。
- Recruiter Web 能看到同一条动态。
- Recruiter 成功点赞并发表评论。
- Candidate Android 能看到该点赞和评论。
- Candidate 点赞后，点赞计数为 2；取消点赞后，计数回到 1。
- Candidate 发表评论后，Recruiter Web 能看到该评论。

未手动测试后端故障后的 Retry comments。该场景已由自动化测试覆盖，包括首次评论加载失败和分页失败后重试同一页。

本记录不包含账号、密码、JWT、数据库密码或其他密钥。
