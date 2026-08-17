# AD Project 服务器职位一键导入包

这个压缩包包含：

- `jobs.jsonl`：已经清洗并平衡抽样的职位。
- `manifest.json`：职位数量、随机种子和 SHA-256。
- `import_job_bundle.py`：只依赖 Python 3 标准库的导入脚本。

## 一条命令导入

Windows PowerShell：

```powershell
python .\import_job_bundle.py `
  --backend-url https://你的服务器地址 `
  --email 招聘方测试账号邮箱
```

Linux/macOS：

```bash
python3 ./import_job_bundle.py \
  --backend-url https://你的服务器地址 \
  --email 招聘方测试账号邮箱
```

脚本会安全提示输入密码。账号必须是 `RECRUITER`，对应公司必须已经由管理员审核为 `APPROVED`。

成功后应显示 `created` 和 `published`。再次运行同一目录中的脚本时，会读取 `import-state.json` 并跳过已导入职位，不会重复创建。

## 先导入 3 条做测试

```powershell
python .\import_job_bundle.py `
  --backend-url https://你的服务器地址 `
  --email 招聘方测试账号邮箱 `
  --limit 3
```

确认成功后去掉 `--limit 3`，再次运行即可继续导入剩余职位。

## 注意

- 远程服务器必须使用 HTTPS；脚本只允许 localhost 使用 HTTP。
- 不要把密码写进命令、脚本或聊天记录。
- 不要删除生成的 `import-state.json`，否则再次导入可能产生重复职位。
- 数据只能在确认原始数据许可允许团队内部分享后交给队友。
- 导入完成后，ML 从 MySQL 读取职位；服务器推理不再需要这个压缩包。
