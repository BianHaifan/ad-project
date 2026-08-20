-- Local demo data for the adproject MySQL schema.
-- Every demo account uses the password: password
-- This script is destructive: it clears all business data but preserves Flyway history.

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE agent_steps;
TRUNCATE TABLE agent_runs;
TRUNCATE TABLE conversation_read_states;
TRUNCATE TABLE messages;
TRUNCATE TABLE conversations;
TRUNCATE TABLE idempotency_records;
TRUNCATE TABLE application_status_events;
TRUNCATE TABLE applications;
TRUNCATE TABLE resume_snapshots;
TRUNCATE TABLE resumes;
TRUNCATE TABLE candidate_profiles;
TRUNCATE TABLE job_audit_events;
TRUNCATE TABLE jobs;
TRUNCATE TABLE refresh_tokens;
TRUNCATE TABLE company_members;
TRUNCATE TABLE companies;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS = 1;

SET @password_hash = '$2a$12$AnAsV/cb78zYQshjiKBkHOI1An3SXamixKMaqPQL4hWudJv8xNyem';
SET @now = UTC_TIMESTAMP(6);

INSERT INTO users
    (id, email, password_hash, full_name, role, status, accepted_terms_version, avatar_url, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'recruiter@demo.local', @password_hash,
     'Maya Chen', 'RECRUITER', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 120 DAY, @now - INTERVAL 2 HOUR),
    ('10000000-0000-0000-0000-000000000002', 'recruiter2@demo.local', @password_hash,
     'Daniel Wong', 'RECRUITER', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 90 DAY, @now - INTERVAL 1 DAY),
    ('10000000-0000-0000-0000-000000000101', 'alice@demo.local', @password_hash,
     'Alice Zhang', 'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 60 DAY, @now - INTERVAL 20 MINUTE),
    ('10000000-0000-0000-0000-000000000102', 'bob@demo.local', @password_hash,
     'Bob Lim', 'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 45 DAY, @now - INTERVAL 35 MINUTE);

INSERT INTO companies
    (id, name, logo_url, stage, employee_range, verification_status, website, description, location,
     version, created_by, created_at, updated_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'NovaWorks Technology', NULL, 'SERIES_B', '201-500',
     'APPROVED', 'https://example.com/novaworks',
     'A product engineering company building cloud platforms for teams across Asia.', 'Singapore',
     2, '10000000-0000-0000-0000-000000000001', @now - INTERVAL 120 DAY, @now - INTERVAL 30 DAY);

INSERT INTO company_members (id, company_id, user_id, member_role, created_at)
VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 'ADMIN', @now - INTERVAL 120 DAY),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', 'ADMIN', @now - INTERVAL 90 DAY);

INSERT INTO candidate_profiles (user_id, headline, location, version, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000101', 'Full-stack engineer · React · Spring Boot', 'Shanghai',
     2, @now - INTERVAL 60 DAY, @now - INTERVAL 20 MINUTE),
    ('10000000-0000-0000-0000-000000000102', 'Android engineer · Kotlin · Jetpack Compose', 'Singapore',
     1, @now - INTERVAL 45 DAY, @now - INTERVAL 35 MINUTE);

INSERT INTO resumes
    (id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     version, created_at, updated_at, skills_json)
VALUES
    ('50000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000101',
     'Alice Zhang', 25, 'Shanghai', 'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY,
     '["React","TypeScript","Spring Boot","Java","MySQL"]'),
    ('50000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000102',
     'Bob Lim', 27, 'Singapore', 'Android engineer · Kotlin · Jetpack Compose',
     'Android developer focused on Compose architecture, app performance, accessibility, and dependable API integration.',
     '[{"experienceId":"exp-bob-1","title":"Android Developer","company":"TransitGo","description":"Led a Compose migration and improved cold-start performance by 30 percent.","startDate":"2022-03","endDate":null}]',
     1, @now - INTERVAL 44 DAY, @now - INTERVAL 2 DAY,
     '["Kotlin","Jetpack Compose","Coroutines","Retrofit","Android"]');

INSERT INTO jobs
    (id, company_id, created_by, owner_id, title, employment_type, workplace_type, location,
     salary_min, salary_max, salary_currency, salary_period, description, requirements_json, skills_json,
     deadline, visibility, status, applicant_count, published_at, version, created_at, updated_at)
VALUES
    ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Full-stack Software Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 6500, 9000, 'SGD', 'MONTH',
     'Build customer-facing recruiting workflows and scalable Spring Boot services in a collaborative product team.',
     '["3+ years of software engineering experience","Experience shipping production web applications","Strong API and database fundamentals"]',
     '["Java","Spring Boot","React","TypeScript","MySQL"]', @now + INTERVAL 35 DAY, 'PUBLIC', 'ACTIVE', 1,
     @now - INTERVAL 20 DAY, 2, @now - INTERVAL 22 DAY, @now - INTERVAL 20 MINUTE),
    ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Senior Android Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 7000, 9800, 'SGD', 'MONTH',
     'Own key Android experiences, evolve our Compose design system, and improve app quality and performance.',
     '["4+ years of Android development","Production Jetpack Compose experience","Strong testing and architecture skills"]',
     '["Kotlin","Jetpack Compose","Coroutines","Retrofit","Android"]', @now + INTERVAL 28 DAY, 'PUBLIC', 'ACTIVE', 1,
     @now - INTERVAL 15 DAY, 2, @now - INTERVAL 17 DAY, @now - INTERVAL 35 MINUTE),
    ('40000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Data Science Intern', 'INTERNSHIP', 'ONSITE', 'Singapore', 1800, 2400, 'SGD', 'MONTH',
     'Support recommendation experiments, dataset preparation, offline evaluation, and model documentation.',
     '["Currently studying computer science or a related discipline","Comfortable with Python and SQL"]',
     '["Python","Pandas","scikit-learn","SQL"]', @now + INTERVAL 50 DAY, 'PRIVATE', 'DRAFT', 0,
     NULL, 1, @now - INTERVAL 3 DAY, @now - INTERVAL 3 DAY),
    ('40000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Cloud Platform Engineer', 'FULL_TIME', 'REMOTE', 'Singapore', 7200, 10200, 'SGD', 'MONTH',
     'Improve delivery pipelines and operate resilient cloud infrastructure for product engineering teams.',
     '["Experience operating cloud infrastructure","Strong Linux and container fundamentals"]',
     '["AWS","Docker","Kubernetes","Terraform","GitHub Actions"]', @now - INTERVAL 2 DAY, 'PUBLIC', 'CLOSED', 1,
     @now - INTERVAL 40 DAY, 3, @now - INTERVAL 42 DAY, @now - INTERVAL 2 DAY),
    ('40000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Product Designer', 'FULL_TIME', 'HYBRID', 'Singapore', 5500, 8200, 'SGD', 'MONTH',
     'Design end-to-end candidate and recruiter experiences together with product and engineering.',
     '["3+ years of product design experience","Strong portfolio of shipped web or mobile products","Figma fluency and prototyping skills"]',
     '["Figma","UI Design","UX Research","Prototyping","Design Systems"]', @now + INTERVAL 38 DAY, 'PUBLIC', 'ACTIVE', 3,
     @now - INTERVAL 12 DAY, 2, @now - INTERVAL 13 DAY, @now - INTERVAL 1 DAY),
    ('40000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Backend Engineer (Java)', 'FULL_TIME', 'REMOTE', 'Singapore', 6800, 9500, 'SGD', 'MONTH',
     'Design and operate Java services that power high-traffic recruiting workflows across the region.',
     '["3+ years of Java backend development","Solid Spring Boot and relational database experience","Comfortable with CI/CD and containerised deployments"]',
     '["Java","Spring Boot","MySQL","Redis","Docker"]', @now + INTERVAL 32 DAY, 'PUBLIC', 'ACTIVE', 2,
     @now - INTERVAL 10 DAY, 2, @now - INTERVAL 11 DAY, @now - INTERVAL 12 HOUR),
    ('40000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Frontend Engineer (React)', 'FULL_TIME', 'HYBRID', 'Singapore', 6000, 8800, 'SGD', 'MONTH',
     'Build the recruiter web workspace with React and TypeScript, focusing on quality and accessibility.',
     '["3+ years of frontend development","Production React and TypeScript experience","Strong sense for UI quality and accessibility"]',
     '["React","TypeScript","Vite","Tailwind CSS","Testing Library"]', @now + INTERVAL 40 DAY, 'PUBLIC', 'ACTIVE', 4,
     @now - INTERVAL 8 DAY, 2, @now - INTERVAL 9 DAY, @now - INTERVAL 4 HOUR),
    ('40000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'DevOps Engineer', 'FULL_TIME', 'REMOTE', 'Singapore', 7000, 10000, 'SGD', 'MONTH',
     'Own delivery pipelines, observability and cost for services used by product engineering teams.',
     '["Experience operating cloud infrastructure in production","Strong Linux, networking and container fundamentals","Infrastructure-as-code mindset"]',
     '["AWS","Kubernetes","Terraform","GitHub Actions","Prometheus"]', @now + INTERVAL 25 DAY, 'PUBLIC', 'ACTIVE', 1,
     @now - INTERVAL 6 DAY, 2, @now - INTERVAL 7 DAY, @now - INTERVAL 1 DAY),
    ('40000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Data Analyst', 'FULL_TIME', 'ONSITE', 'Singapore', 4800, 7000, 'SGD', 'MONTH',
     'Turn product and hiring data into dashboards and insights that guide company decisions.',
     '["2+ years of data analysis experience","Proficient in SQL and at least one BI tool","Clear written communication of findings"]',
     '["SQL","Python","Metabase","Excel","A/B Testing"]', @now + INTERVAL 45 DAY, 'PUBLIC', 'ACTIVE', 2,
     @now - INTERVAL 5 DAY, 2, @now - INTERVAL 6 DAY, @now - INTERVAL 8 HOUR),
    ('40000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'QA Automation Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 5200, 7800, 'SGD', 'MONTH',
     'Build reliable automated test suites and release gates across web and mobile products.',
     '["3+ years of QA automation experience","Hands-on with API and UI test frameworks","Detail-oriented and curious about failure modes"]',
     '["Selenium","Playwright","JUnit","Postman","CI/CD"]', @now + INTERVAL 30 DAY, 'PUBLIC', 'ACTIVE', 1,
     @now - INTERVAL 3 DAY, 2, @now - INTERVAL 4 DAY, @now - INTERVAL 2 HOUR),
    ('40000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'AI/ML Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 7500, 11000, 'SGD', 'MONTH',
     'Build and productionise recommendation models that help candidates find the right roles.',
     '["3+ years of ML engineering experience","Strong Python and model serving skills","Experience with recommendation or ranking systems"]',
     '["Python","PyTorch","FastAPI","Feature Engineering","MLOps"]', @now + INTERVAL 28 DAY, 'PUBLIC', 'ACTIVE', 1,
     @now - INTERVAL 2 DAY, 2, @now - INTERVAL 3 DAY, @now - INTERVAL 1 HOUR),
    ('40000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Customer Success Intern', 'INTERNSHIP', 'ONSITE', 'Singapore', 1400, 1800, 'SGD', 'MONTH',
     'Support recruiter onboarding, respond to product questions and help improve help-centre content.',
     '["Currently enrolled in a university programme","Strong written English and Chinese","Service mindset and attention to detail"]',
     '["Communication","CRM","Zendesk","Content Writing"]', @now + INTERVAL 60 DAY, 'PUBLIC', 'ACTIVE', 5,
     @now - INTERVAL 1 DAY, 2, @now - INTERVAL 2 DAY, @now - INTERVAL 30 MINUTE),
    ('40000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'UI Engineer (Part-time)', 'PART_TIME', 'REMOTE', 'Singapore', 2500, 4000, 'SGD', 'MONTH',
     'Implement and polish design-system components for the recruiter workspace, around 20 hours per week.',
     '["Proficiency with React and CSS","Portfolio of polished UI work","Available at least 20 hours per week"]',
     '["React","CSS","Storybook","Figma"]', @now + INTERVAL 20 DAY, 'PUBLIC', 'PAUSED', 2,
     @now - INTERVAL 20 DAY, 3, @now - INTERVAL 21 DAY, @now - INTERVAL 1 DAY),
    ('40000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Solutions Architect', 'FULL_TIME', 'HYBRID', 'Singapore', 9000, 13000, 'SGD', 'MONTH',
     'Partner with enterprise customers to design integration and rollout plans for the platform.',
     '["6+ years of software architecture experience","Enterprise integration and API design background","Strong customer-facing communication"]',
     '["System Design","APIs","Cloud Architecture","Technical Sales"]', @now + INTERVAL 21 DAY, 'PRIVATE', 'DRAFT', 0,
     NULL, 1, @now - INTERVAL 2 DAY, @now - INTERVAL 2 DAY),
    ('40000000-0000-0000-0000-000000000015', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Mobile Engineer (iOS)', 'FULL_TIME', 'REMOTE', 'Shanghai', 6500, 9300, 'SGD', 'MONTH',
     'Build the iOS companion app with SwiftUI, working closely with design and the Android team.',
     '["3+ years of iOS development","Production Swift and SwiftUI experience","Solid testing and release-management practice"]',
     '["Swift","SwiftUI","Xcode","Fastlane","iOS"]', @now - INTERVAL 5 DAY, 'PUBLIC', 'CLOSED', 6,
     @now - INTERVAL 45 DAY, 3, @now - INTERVAL 47 DAY, @now - INTERVAL 3 DAY);

INSERT INTO job_audit_events
    (id, job_id, actor_id, company_id, action, from_status, to_status, occurred_at, reason, request_id)
VALUES
    ('41000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 20 DAY, 'Approved for public hiring', 'seed-job-1'),
    ('41000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 15 DAY, 'Hiring plan approved', 'seed-job-2'),
    ('41000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 40 DAY, 'Role opened', 'seed-job-4-publish'),
    ('41000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_STATUS_CHANGED', 'ACTIVE', 'CLOSED', @now - INTERVAL 2 DAY, 'Hiring cycle completed', 'seed-job-4-close'),
    ('41000000-0000-0000-0000-000000000005', '40000000-0000-0000-0000-000000000005',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 12 DAY, 'Product hiring kickoff', 'seed-job-5-publish'),
    ('41000000-0000-0000-0000-000000000006', '40000000-0000-0000-0000-000000000006',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 10 DAY, 'Backend capacity expanded', 'seed-job-6-publish'),
    ('41000000-0000-0000-0000-000000000007', '40000000-0000-0000-0000-000000000007',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 8 DAY, 'Web workspace growth plan', 'seed-job-7-publish'),
    ('41000000-0000-0000-0000-000000000008', '40000000-0000-0000-0000-000000000008',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 6 DAY, 'Platform reliability investment', 'seed-job-8-publish'),
    ('41000000-0000-0000-0000-000000000009', '40000000-0000-0000-0000-000000000009',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 5 DAY, 'Data-driven decisions initiative', 'seed-job-9-publish'),
    ('41000000-0000-0000-0000-000000000010', '40000000-0000-0000-0000-000000000010',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 3 DAY, 'Quality gates rollout', 'seed-job-10-publish'),
    ('41000000-0000-0000-0000-000000000011', '40000000-0000-0000-0000-000000000011',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 2 DAY, 'Recommendation team expansion', 'seed-job-11-publish'),
    ('41000000-0000-0000-0000-000000000012', '40000000-0000-0000-0000-000000000012',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 1 DAY, 'Internship programme opened', 'seed-job-12-publish'),
    ('41000000-0000-0000-0000-000000000013', '40000000-0000-0000-0000-000000000013',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 20 DAY, 'Design system support role', 'seed-job-13-publish'),
    ('41000000-0000-0000-0000-000000000014', '40000000-0000-0000-0000-000000000013',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'JOB_STATUS_CHANGED', 'ACTIVE', 'PAUSED', @now - INTERVAL 1 DAY, 'Paused while budget is reviewed', 'seed-job-13-pause'),
    ('41000000-0000-0000-0000-000000000015', '40000000-0000-0000-0000-000000000015',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_PUBLISHED', 'DRAFT', 'ACTIVE', @now - INTERVAL 45 DAY, 'iOS team kickoff', 'seed-job-15-publish'),
    ('41000000-0000-0000-0000-000000000016', '40000000-0000-0000-0000-000000000015',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'JOB_STATUS_CHANGED', 'ACTIVE', 'CLOSED', @now - INTERVAL 3 DAY, 'Position filled', 'seed-job-15-close');

INSERT INTO resume_snapshots
    (id, resume_id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     resume_version, resume_created_at, resume_updated_at, captured_at, skills_json)
VALUES
    ('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000101',
     '10000000-0000-0000-0000-000000000101', 'Alice Zhang', 25, 'Shanghai',
     'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY, @now - INTERVAL 8 DAY,
     '["React","TypeScript","Spring Boot","Java","MySQL"]'),
    ('60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000102',
     '10000000-0000-0000-0000-000000000102', 'Bob Lim', 27, 'Singapore',
     'Android engineer · Kotlin · Jetpack Compose',
     'Android developer focused on Compose architecture, app performance, accessibility, and dependable API integration.',
     '[{"experienceId":"exp-bob-1","title":"Android Developer","company":"TransitGo","description":"Led a Compose migration and improved cold-start performance by 30 percent.","startDate":"2022-03","endDate":null}]',
     1, @now - INTERVAL 44 DAY, @now - INTERVAL 2 DAY, @now - INTERVAL 6 DAY,
     '["Kotlin","Jetpack Compose","Coroutines","Retrofit","Android"]'),
    ('60000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000101',
     '10000000-0000-0000-0000-000000000101', 'Alice Zhang', 25, 'Shanghai',
     'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY, @now - INTERVAL 25 DAY,
     '["React","TypeScript","Spring Boot","Java","MySQL"]');

INSERT INTO applications
    (id, job_id, candidate_id, resume_id, resume_snapshot_id, contact_email, share_profile,
     status, applied_at, updated_at, version)
VALUES
    ('70000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000101', '50000000-0000-0000-0000-000000000101',
     '60000000-0000-0000-0000-000000000001', 'alice@demo.local', TRUE,
     'IN_REVIEW', @now - INTERVAL 8 DAY, @now - INTERVAL 20 MINUTE, 2),
    ('70000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000102', '50000000-0000-0000-0000-000000000102',
     '60000000-0000-0000-0000-000000000002', 'bob@demo.local', TRUE,
     'INTERVIEW', @now - INTERVAL 6 DAY, @now - INTERVAL 35 MINUTE, 3),
    ('70000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000101', '50000000-0000-0000-0000-000000000101',
     '60000000-0000-0000-0000-000000000003', 'alice@demo.local', TRUE,
     'REJECTED', @now - INTERVAL 25 DAY, @now - INTERVAL 3 DAY, 3);

INSERT INTO application_status_events
    (id, application_id, actor_id, company_id, from_status, to_status, occurred_at, reason, request_id)
VALUES
    ('80000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000001',
     NULL, 'APPLIED', @now - INTERVAL 8 DAY, 'Application submitted', 'seed-app-1-submit'),
    ('80000000-0000-0000-0000-000000000002', '70000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'APPLIED', 'IN_REVIEW', @now - INTERVAL 4 DAY, 'Profile matches the initial requirements', 'seed-app-1-review'),
    ('80000000-0000-0000-0000-000000000003', '70000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000102', '20000000-0000-0000-0000-000000000001',
     NULL, 'APPLIED', @now - INTERVAL 6 DAY, 'Application submitted', 'seed-app-2-submit'),
    ('80000000-0000-0000-0000-000000000004', '70000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'APPLIED', 'IN_REVIEW', @now - INTERVAL 3 DAY, 'Strong Android background', 'seed-app-2-review'),
    ('80000000-0000-0000-0000-000000000005', '70000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     'IN_REVIEW', 'INTERVIEW', @now - INTERVAL 1 DAY, 'Proceed to technical interview', 'seed-app-2-interview'),
    ('80000000-0000-0000-0000-000000000006', '70000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000001',
     NULL, 'APPLIED', @now - INTERVAL 25 DAY, 'Application submitted', 'seed-app-3-submit'),
    ('80000000-0000-0000-0000-000000000007', '70000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'APPLIED', 'IN_REVIEW', @now - INTERVAL 20 DAY, 'Application reviewed', 'seed-app-3-review'),
    ('80000000-0000-0000-0000-000000000008', '70000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     'IN_REVIEW', 'REJECTED', @now - INTERVAL 3 DAY, 'Role requirements changed', 'seed-app-3-rejected');

INSERT INTO conversations
    (id, application_id, job_id, candidate_id, company_id, created_at, updated_at, last_message_at)
VALUES
    ('90000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000101',
     '20000000-0000-0000-0000-000000000001', @now - INTERVAL 8 DAY, @now - INTERVAL 20 MINUTE, @now - INTERVAL 20 MINUTE),
    ('90000000-0000-0000-0000-000000000002', '70000000-0000-0000-0000-000000000002',
     '40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000102',
     '20000000-0000-0000-0000-000000000001', @now - INTERVAL 6 DAY, @now - INTERVAL 35 MINUTE, @now - INTERVAL 35 MINUTE),
    ('90000000-0000-0000-0000-000000000003', '70000000-0000-0000-0000-000000000003',
     '40000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000101',
     '20000000-0000-0000-0000-000000000001', @now - INTERVAL 25 DAY, @now - INTERVAL 3 DAY, @now - INTERVAL 4 DAY);

INSERT INTO messages
    (id, conversation_id, sender_id, sender_type, body, sent_at, client_message_id, idempotency_key, payload_hash)
VALUES
    ('a0000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 'RECRUITER',
     'Hi Alice, thanks for applying. Your product and Java experience stood out to us.', @now - INTERVAL 2 DAY,
     'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', SHA2('seed-message-1', 256)),
    ('a0000000-0000-0000-0000-000000000002', '90000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000101', 'CANDIDATE',
     'Thank you, Maya. I would be happy to share more about the platform work I led.', @now - INTERVAL 1 DAY,
     'b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002', SHA2('seed-message-2', 256)),
    ('a0000000-0000-0000-0000-000000000003', '90000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 'RECRUITER',
     'Great. Are you available for a 30-minute introductory call next Tuesday afternoon?', @now - INTERVAL 45 MINUTE,
     'b0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000003', SHA2('seed-message-3', 256)),
    ('a0000000-0000-0000-0000-000000000004', '90000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000101', 'CANDIDATE',
     'Tuesday at 3 PM Singapore time works well for me.', @now - INTERVAL 20 MINUTE,
     'b0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000004', SHA2('seed-message-4', 256)),
    ('a0000000-0000-0000-0000-000000000005', '90000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000002', 'RECRUITER',
     'Hi Bob, we would like to invite you to a technical interview for the Android role.', @now - INTERVAL 1 DAY,
     'b0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000005', SHA2('seed-message-5', 256)),
    ('a0000000-0000-0000-0000-000000000006', '90000000-0000-0000-0000-000000000002',
     '10000000-0000-0000-0000-000000000102', 'CANDIDATE',
     'Thanks Daniel. I am available Thursday morning and look forward to speaking with the team.', @now - INTERVAL 35 MINUTE,
     'b0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000006', SHA2('seed-message-6', 256)),
    ('a0000000-0000-0000-0000-000000000007', '90000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000101', 'CANDIDATE',
     'Hello, could you share more about the on-call expectations for this role?', @now - INTERVAL 10 DAY,
     'b0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000007', SHA2('seed-message-7', 256)),
    ('a0000000-0000-0000-0000-000000000008', '90000000-0000-0000-0000-000000000003',
     '10000000-0000-0000-0000-000000000001', 'RECRUITER',
     'The rotation was one week in six. The position has now closed, but thank you for your interest.', @now - INTERVAL 4 DAY,
     'b0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000008', SHA2('seed-message-8', 256));

INSERT INTO conversation_read_states (conversation_id, user_id, last_read_message_id, updated_at)
VALUES
    ('90000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-000000000003', @now - INTERVAL 30 MINUTE),
    ('90000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000101',
     'a0000000-0000-0000-0000-000000000002', @now - INTERVAL 1 HOUR),
    ('90000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000102',
     'a0000000-0000-0000-0000-000000000005', @now - INTERVAL 2 HOUR),
    ('90000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001',
     'a0000000-0000-0000-0000-000000000007', @now - INTERVAL 9 DAY);
