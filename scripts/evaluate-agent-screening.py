#!/usr/bin/env python3
"""Screen every job in the local demo DB through the HR agent and score the recommendations.

Ground truth: each resume's skills_json vs each job's skills_json. Exact token overlap is a
crude but transparent proxy for "fit" — synonyms (Valkey/Redis) won't match, so misses here
don't necessarily mean the agent ranked badly.
"""
import json
import subprocess
import sys
import time
import urllib.request

BASE = "http://localhost:8080/api/v1"
MYSQL = ["docker", "exec", "adproject-agent-latest-mysql", "mysql",
         "-uadproject", "-plocal_agent_app", "adproject", "-N", "-e"]


def mysql(sql):
    out = subprocess.run(MYSQL + [sql], capture_output=True, text=True).stdout
    return [line.split("\t") for line in out.splitlines() if line.strip()]


def api(method, path, token, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, data=data, timeout=130) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else None


def login():
    body = json.dumps({"email": "recruiter@demo.local", "password": "password"}).encode()
    req = urllib.request.Request(BASE + "/auth/login", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())["data"]["accessToken"]


def norm(skills):
    return {s.strip().lower() for s in skills}


def main():
    jobs = mysql("SELECT id, title, skills_json FROM jobs ORDER BY title")
    if len(sys.argv) > 1:
        titles = set(sys.argv[1:])
        jobs = [job for job in jobs if job[1] in titles]
        missing = titles - {job[1] for job in jobs}
        if missing:
            sys.exit(f"unknown job titles: {sorted(missing)}")
    resumes = mysql("SELECT full_name, skills_json FROM resumes")
    if not jobs or not resumes:
        sys.exit("no jobs/resumes in the demo DB")
    candidates = {name: norm(json.loads(skills)) for name, skills in resumes}
    job_skills = {title: norm(json.loads(skills)) for _, title, skills in jobs}
    token = login()

    rows = []
    hits1 = hits3 = 0
    latencies = []
    clarifications = failures = 0
    for _job_id, title, _skills_json in jobs:
        # ground truth fit = at least one shared skill token with the job
        fit = {name for name, skills in candidates.items() if skills & job_skills[title]}
        best = sorted(fit, key=lambda n: len(candidates[n] & job_skills[title]), reverse=True)

        start = time.time()
        run = api("POST", "/agent/runs", token,
                  {"instruction": f"Screen candidates for the {title} role",
                   "timezone": "Asia/Shanghai"})
        elapsed = time.time() - start
        data = run["data"]
        status = data["status"]
        latencies.append(elapsed)
        ranked = data["screening"]["ranked"] if data.get("screening") else []
        if status == "NEEDS_CLARIFICATION":
            clarifications += 1
            rows.append((title, status, elapsed, ranked, best, data["message"]))
            continue
        if status == "FAILED":
            failures += 1
            rows.append((title, f"{status}:{data.get('errorCode')}", elapsed, ranked, best,
                         data["message"]))
            continue

        names = [r["fullName"] for r in ranked]
        top1 = names[0] if names else None
        top3 = set(names[:3])
        h1 = top1 in fit
        h3 = bool(top3 & fit)
        hits1 += h1
        hits3 += h3
        rows.append((title, status, elapsed, ranked, best, None))

    for title, status, elapsed, ranked, best, note in rows:
        print(f"\n### {title}  [{status}]  {elapsed:.1f}s")
        if note:
            print(f"    {note}")
        for r in ranked[:5]:
            shared = sorted(candidates.get(r["fullName"], set()) & job_skills[title])
            marker = "  <<< matches job skills" if shared else ""
            print(f"    #{r['rank']:>2} {r['fullName']:<18} matched={shared if shared else '-'}{marker}")
            if r.get("recommendation"):
                print(f"        reason: {r['recommendation']}")
        if ranked:
            print(f"    ground-truth fit: {sorted(best)}")

    print("\n" + "=" * 100)
    done = len(rows) - clarifications - failures
    print(f"jobs: {len(rows)}  completed: {done}  clarified: {clarifications}  failed: {failures}")
    if latencies:
        latencies.sort()
        print(f"latency  min={latencies[0]:.1f}s  median={latencies[len(latencies)//2]:.1f}s  "
              f"max={latencies[-1]:.1f}s")
    if done:
        print(f"hit@1 (top pick shares a skill with the job): {hits1}/{done}")
        print(f"hit@3 (a ground-truth fit appears in the top 3): {hits3}/{done}")


if __name__ == "__main__":
    main()
