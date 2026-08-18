# 认证、Community、Android 与品牌实现决策

## Android 图标映射确认

已确认并按计划中的建议表实现，源 SVG 保留不变，Android 使用复制后的 ASCII 资源名：

| 用途 | 未选中/普通 | 选中/激活 |
| --- | --- | --- |
| Jobs | `hirex_jobs_inactive` | `hirex_jobs_active` |
| Community | `hirex_community_inactive` | `hirex_community_active` |
| Messages | `hirex_messages_inactive` | `hirex_messages_active` |
| Me | `hirex_me_inactive` | `hirex_me_active` |
| 收藏 | `hirex_star_inactive` | `hirex_star_active` |
| 点赞 | `hirex_like_inactive` | `hirex_like_active` |

搜索、筛选、刷新和发帖分别使用 `hirex_search`、`hirex_filter`、`hirex_refresh`、`hirex_add`。返回、头像和简历等既有专用图标未替换。

## 邮件发送方案确认

- 本地默认不配置真实邮箱；密码重置请求返回明确的 `503 PASSWORD_RESET_EMAIL_NOT_CONFIGURED`，不会伪造发送成功。
- 自动化测试使用进程内 fake sender，仅捕获验证码用于安全边界验证，不输出验证码到日志、响应或报告。
- staging/production 使用 Spring Mail SMTP adapter，通过 `SMTP_HOST`、`SMTP_PORT`、`SMTP_USERNAME`、`SMTP_PASSWORD`、`SMTP_FROM`、`SMTP_STARTTLS` 注入。
- 部署凭据必须由环境或密钥服务提供，不写入仓库；默认建议使用独立事务邮件账号并启用 STARTTLS。

## 当前人工验收前提

本机当前未配置 SMTP 凭据，因此可以人工验证“未配置时安全失败”和 Community 跨端流程，但真实邮件投递仍需 staging 测试账号。
