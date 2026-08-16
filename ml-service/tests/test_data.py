from ad_recommender.data import load_jobs, load_resumes


def test_resume_loader_drops_broken_and_unknown_rows(tmp_path):
    source = tmp_path / "resumes.csv"
    source.write_text(
        "ID,Resume_str,Resume_html,Category\n"
        '1,"Python software engineer with ten years of backend platform experience and SQL skills. '
        'Built services and led delivery across multiple companies.",,INFORMATION-TECHNOLOGY\n'
        'broken,"tiny",,Oracle database\n',
        encoding="utf-8",
    )

    rows = load_resumes(source)

    assert len(rows) == 1
    assert rows[0]["resume_id"] == "1"
    assert rows[0]["category"] == "INFORMATION-TECHNOLOGY"


def test_job_loader_rejects_cross_row_requirements_and_verb_react(tmp_path):
    source = tmp_path / "jobs.csv"
    source.write_text(
        "job_id,title,description,location,work_type,skills_desc,remote_allowed\n"
        '1,Revenue Manager,"Manage hospital billing and positively react to stress",Singapore,'
        'FULL_TIME,"Search engine marketing campaign analytics and SEO",0\n',
        encoding="utf-8",
    )

    job = load_jobs(source)[0]

    assert job.requirements == []
    assert "react" not in job.skills
    assert "seo" not in job.skills


def test_job_loader_keeps_react_with_technical_context(tmp_path):
    source = tmp_path / "jobs.csv"
    source.write_text(
        "job_id,title,description,location,work_type,skills_desc,remote_allowed\n"
        '2,Frontend Engineer,"Build React and TypeScript web applications",Singapore,'
        'FULL_TIME,"React development experience",1\n',
        encoding="utf-8",
    )

    job = load_jobs(source)[0]

    assert "react" in job.skills
    assert "typescript" in job.skills
    assert job.requirements == ["React development experience"]
