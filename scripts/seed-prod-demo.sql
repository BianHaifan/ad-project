-- Production demo seed: base demo data (NovaWorks, two recruiters, 15 jobs) plus
-- 20 candidates with diverse tech stacks and domains.
-- Non-destructive: INSERT IGNORE only, safe to re-run; existing rows are never changed.
-- Demo accounts sign in with the shared demo password: password

SET @password_hash = '$2a$12$AnAsV/cb78zYQshjiKBkHOI1An3SXamixKMaqPQL4hWudJv8xNyem';
SET @now = UTC_TIMESTAMP(6);

-- ---------------------------------------------------------------------------
-- Base demo data (skipped when already present)
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO users
    (id, email, password_hash, full_name, role, status, accepted_terms_version, avatar_url, created_at, updated_at)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'recruiter@demo.local', @password_hash,
     'Maya Chen', 'RECRUITER', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 120 DAY, @now - INTERVAL 2 HOUR),
    ('10000000-0000-0000-0000-000000000002', 'recruiter2@demo.local', @password_hash,
     'Daniel Wong', 'RECRUITER', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 90 DAY, @now - INTERVAL 1 DAY);

INSERT IGNORE INTO companies
    (id, name, logo_url, stage, employee_range, verification_status, website, description, location,
     version, created_by, created_at, updated_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'NovaWorks Technology', NULL, 'SERIES_B', '201-500',
     'APPROVED', 'https://example.com/novaworks',
     'A product engineering company building cloud platforms for teams across Asia.', 'Singapore',
     2, '10000000-0000-0000-0000-000000000001', @now - INTERVAL 120 DAY, @now - INTERVAL 30 DAY);

INSERT IGNORE INTO company_members (id, company_id, user_id, member_role, created_at)
VALUES
    ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', 'ADMIN', @now - INTERVAL 120 DAY),
    ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', 'ADMIN', @now - INTERVAL 90 DAY);

INSERT IGNORE INTO jobs
    (id, company_id, created_by, owner_id, title, employment_type, workplace_type, location,
     salary_min, salary_max, salary_currency, salary_period, description, requirements_json, skills_json,
     deadline, visibility, status, applicant_count, published_at, version, created_at, updated_at)
VALUES
    ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Full-stack Software Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 6500, 9000, 'SGD', 'MONTH',
     'Build customer-facing recruiting workflows and scalable Spring Boot services in a collaborative product team.',
     '["3+ years of software engineering experience","Experience shipping production web applications","Strong API and database fundamentals"]',
     '["Java","Spring Boot","React","TypeScript","MySQL"]', @now + INTERVAL 35 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 20 DAY, 2, @now - INTERVAL 22 DAY, @now - INTERVAL 20 MINUTE),
    ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Senior Android Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 7000, 9800, 'SGD', 'MONTH',
     'Own key Android experiences, evolve our Compose design system, and improve app quality and performance.',
     '["4+ years of Android development","Production Jetpack Compose experience","Strong testing and architecture skills"]',
     '["Kotlin","Jetpack Compose","Coroutines","Retrofit","Android"]', @now + INTERVAL 28 DAY, 'PUBLIC', 'ACTIVE', 0,
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
     '["AWS","Docker","Kubernetes","Terraform","GitHub Actions"]', @now - INTERVAL 2 DAY, 'PUBLIC', 'CLOSED', 0,
     @now - INTERVAL 40 DAY, 3, @now - INTERVAL 42 DAY, @now - INTERVAL 2 DAY),
    ('40000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Product Designer', 'FULL_TIME', 'HYBRID', 'Singapore', 5500, 8200, 'SGD', 'MONTH',
     'Design end-to-end candidate and recruiter experiences together with product and engineering.',
     '["3+ years of product design experience","Strong portfolio of shipped web or mobile products","Figma fluency and prototyping skills"]',
     '["Figma","UI Design","UX Research","Prototyping","Design Systems"]', @now + INTERVAL 38 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 12 DAY, 2, @now - INTERVAL 13 DAY, @now - INTERVAL 1 DAY),
    ('40000000-0000-0000-0000-000000000006', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'Backend Engineer (Java)', 'FULL_TIME', 'REMOTE', 'Singapore', 6800, 9500, 'SGD', 'MONTH',
     'Design and operate Java services that power high-traffic recruiting workflows across the region.',
     '["3+ years of Java backend development","Solid Spring Boot and relational database experience","Comfortable with CI/CD and containerised deployments"]',
     '["Java","Spring Boot","MySQL","Redis","Docker"]', @now + INTERVAL 32 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 10 DAY, 2, @now - INTERVAL 11 DAY, @now - INTERVAL 12 HOUR),
    ('40000000-0000-0000-0000-000000000007', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Frontend Engineer (React)', 'FULL_TIME', 'HYBRID', 'Singapore', 6000, 8800, 'SGD', 'MONTH',
     'Build the recruiter web workspace with React and TypeScript, focusing on quality and accessibility.',
     '["3+ years of frontend development","Production React and TypeScript experience","Strong sense for UI quality and accessibility"]',
     '["React","TypeScript","Vite","Tailwind CSS","Testing Library"]', @now + INTERVAL 40 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 8 DAY, 2, @now - INTERVAL 9 DAY, @now - INTERVAL 4 HOUR),
    ('40000000-0000-0000-0000-000000000008', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'DevOps Engineer', 'FULL_TIME', 'REMOTE', 'Singapore', 7000, 10000, 'SGD', 'MONTH',
     'Own delivery pipelines, observability and cost for services used by product engineering teams.',
     '["Experience operating cloud infrastructure in production","Strong Linux, networking and container fundamentals","Infrastructure-as-code mindset"]',
     '["AWS","Kubernetes","Terraform","GitHub Actions","Prometheus"]', @now + INTERVAL 25 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 6 DAY, 2, @now - INTERVAL 7 DAY, @now - INTERVAL 1 DAY),
    ('40000000-0000-0000-0000-000000000009', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Data Analyst', 'FULL_TIME', 'ONSITE', 'Singapore', 4800, 7000, 'SGD', 'MONTH',
     'Turn product and hiring data into dashboards and insights that guide company decisions.',
     '["2+ years of data analysis experience","Proficient in SQL and at least one BI tool","Clear written communication of findings"]',
     '["SQL","Python","Metabase","Excel","A/B Testing"]', @now + INTERVAL 45 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 5 DAY, 2, @now - INTERVAL 6 DAY, @now - INTERVAL 8 HOUR),
    ('40000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'QA Automation Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 5200, 7800, 'SGD', 'MONTH',
     'Build reliable automated test suites and release gates across web and mobile products.',
     '["3+ years of QA automation experience","Hands-on with API and UI test frameworks","Detail-oriented and curious about failure modes"]',
     '["Selenium","Playwright","JUnit","Postman","CI/CD"]', @now + INTERVAL 30 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 3 DAY, 2, @now - INTERVAL 4 DAY, @now - INTERVAL 2 HOUR),
    ('40000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'AI/ML Engineer', 'FULL_TIME', 'HYBRID', 'Singapore', 7500, 11000, 'SGD', 'MONTH',
     'Build and productionise recommendation models that help candidates find the right roles.',
     '["3+ years of ML engineering experience","Strong Python and model serving skills","Experience with recommendation or ranking systems"]',
     '["Python","PyTorch","FastAPI","Feature Engineering","MLOps"]', @now + INTERVAL 28 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 2 DAY, 2, @now - INTERVAL 3 DAY, @now - INTERVAL 1 HOUR),
    ('40000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'Customer Success Intern', 'INTERNSHIP', 'ONSITE', 'Singapore', 1400, 1800, 'SGD', 'MONTH',
     'Support recruiter onboarding, respond to product questions and help improve help-centre content.',
     '["Currently enrolled in a university programme","Strong written English and Chinese","Service mindset and attention to detail"]',
     '["Communication","CRM","Zendesk","Content Writing"]', @now + INTERVAL 60 DAY, 'PUBLIC', 'ACTIVE', 0,
     @now - INTERVAL 1 DAY, 2, @now - INTERVAL 2 DAY, @now - INTERVAL 30 MINUTE),
    ('40000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'UI Engineer (Part-time)', 'PART_TIME', 'REMOTE', 'Singapore', 2500, 4000, 'SGD', 'MONTH',
     'Implement and polish design-system components for the recruiter workspace, around 20 hours per week.',
     '["Proficiency with React and CSS","Portfolio of polished UI work","Available at least 20 hours per week"]',
     '["React","CSS","Storybook","Figma"]', @now + INTERVAL 20 DAY, 'PUBLIC', 'PAUSED', 0,
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
     '["Swift","SwiftUI","Xcode","Fastlane","iOS"]', @now - INTERVAL 5 DAY, 'PUBLIC', 'CLOSED', 0,
     @now - INTERVAL 45 DAY, 3, @now - INTERVAL 47 DAY, @now - INTERVAL 3 DAY);

-- ---------------------------------------------------------------------------
-- Batch 3: twenty candidates with diverse tech stacks and domains
-- ---------------------------------------------------------------------------

INSERT IGNORE INTO users
    (id, email, password_hash, full_name, role, status, accepted_terms_version, avatar_url, created_at, updated_at)
VALUES
 ('10000000-0000-0000-0000-000000000301', 'lena@demo.local',    @password_hash, 'Lena Hoffmann',  'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 300 DAY, @now - INTERVAL 90 MINUTE),
 ('10000000-0000-0000-0000-000000000302', 'arjun@demo.local',   @password_hash, 'Arjun Mehta',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 290 DAY, @now - INTERVAL 85 MINUTE),
 ('10000000-0000-0000-0000-000000000303', 'chloe@demo.local',   @password_hash, 'Chloe Dubois',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 280 DAY, @now - INTERVAL 80 MINUTE),
 ('10000000-0000-0000-0000-000000000304', 'mateo@demo.local',   @password_hash, 'Mateo Garcia',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 270 DAY, @now - INTERVAL 75 MINUTE),
 ('10000000-0000-0000-0000-000000000305', 'hana@demo.local',    @password_hash, 'Hana Kim',       'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 260 DAY, @now - INTERVAL 70 MINUTE),
 ('10000000-0000-0000-0000-000000000306', 'oliver@demo.local',  @password_hash, 'Oliver Smith',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 250 DAY, @now - INTERVAL 65 MINUTE),
 ('10000000-0000-0000-0000-000000000307', 'yuki@demo.local',    @password_hash, 'Yuki Tanaka',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 240 DAY, @now - INTERVAL 60 MINUTE),
 ('10000000-0000-0000-0000-000000000308', 'amara@demo.local',   @password_hash, 'Amara Osei',     'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 230 DAY, @now - INTERVAL 55 MINUTE),
 ('10000000-0000-0000-0000-000000000309', 'lucas@demo.local',   @password_hash, 'Lucas Silva',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 220 DAY, @now - INTERVAL 50 MINUTE),
 ('10000000-0000-0000-0000-000000000310', 'emma@demo.local',    @password_hash, 'Emma Larsson',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 210 DAY, @now - INTERVAL 45 MINUTE),
 ('10000000-0000-0000-0000-000000000311', 'rahul@demo.local',   @password_hash, 'Rahul Nair',     'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 200 DAY, @now - INTERVAL 40 MINUTE),
 ('10000000-0000-0000-0000-000000000312', 'isabella@demo.local', @password_hash, 'Isabella Rossi', 'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 190 DAY, @now - INTERVAL 35 MINUTE),
 ('10000000-0000-0000-0000-000000000313', 'wei@demo.local',     @password_hash, 'Wei Chen',       'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 180 DAY, @now - INTERVAL 30 MINUTE),
 ('10000000-0000-0000-0000-000000000314', 'nadia@demo.local',   @password_hash, 'Nadia Hassan',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 170 DAY, @now - INTERVAL 25 MINUTE),
 ('10000000-0000-0000-0000-000000000315', 'tom@demo.local',     @password_hash, 'Tom Becker',     'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 160 DAY, @now - INTERVAL 20 MINUTE),
 ('10000000-0000-0000-0000-000000000316', 'sofia-p@demo.local', @password_hash, 'Sofia Popescu',  'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 150 DAY, @now - INTERVAL 18 MINUTE),
 ('10000000-0000-0000-0000-000000000317', 'jack@demo.local',    @password_hash, 'Jack Thompson',  'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 140 DAY, @now - INTERVAL 16 MINUTE),
 ('10000000-0000-0000-0000-000000000318', 'aisha@demo.local',   @password_hash, 'Aisha Bello',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 130 DAY, @now - INTERVAL 14 MINUTE),
 ('10000000-0000-0000-0000-000000000319', 'miguel@demo.local',  @password_hash, 'Miguel Torres',  'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 120 DAY, @now - INTERVAL 12 MINUTE),
 ('10000000-0000-0000-0000-000000000320', 'fiona@demo.local',   @password_hash, 'Fiona Gallagher', 'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 110 DAY, @now - INTERVAL 10 MINUTE);

INSERT IGNORE INTO candidate_profiles (user_id, headline, location, version, created_at, updated_at) VALUES
 ('10000000-0000-0000-0000-000000000301', 'Data engineer · Spark · Airflow · dbt', 'Berlin', 3, @now - INTERVAL 300 DAY, @now - INTERVAL 90 MINUTE),
 ('10000000-0000-0000-0000-000000000302', 'ML/NLP engineer · PyTorch · Transformers', 'Bangalore', 4, @now - INTERVAL 290 DAY, @now - INTERVAL 85 MINUTE),
 ('10000000-0000-0000-0000-000000000303', 'iOS engineer · Swift · SwiftUI', 'Paris', 2, @now - INTERVAL 280 DAY, @now - INTERVAL 80 MINUTE),
 ('10000000-0000-0000-0000-000000000304', 'Security engineer · AppSec · OWASP', 'Madrid', 3, @now - INTERVAL 270 DAY, @now - INTERVAL 75 MINUTE),
 ('10000000-0000-0000-0000-000000000305', 'Embedded engineer · C · Rust · RTOS', 'Seoul', 5, @now - INTERVAL 260 DAY, @now - INTERVAL 70 MINUTE),
 ('10000000-0000-0000-0000-000000000306', 'Game developer · Unity · C#', 'London', 2, @now - INTERVAL 250 DAY, @now - INTERVAL 65 MINUTE),
 ('10000000-0000-0000-0000-000000000307', 'Blockchain engineer · Solidity · EVM', 'Tokyo', 3, @now - INTERVAL 240 DAY, @now - INTERVAL 60 MINUTE),
 ('10000000-0000-0000-0000-000000000308', 'SRE · Kubernetes · Prometheus', 'Lagos', 4, @now - INTERVAL 230 DAY, @now - INTERVAL 55 MINUTE),
 ('10000000-0000-0000-0000-000000000309', 'Cloud architect · AWS · GCP', 'Sao Paulo', 5, @now - INTERVAL 220 DAY, @now - INTERVAL 50 MINUTE),
 ('10000000-0000-0000-0000-000000000310', 'Database engineer · PostgreSQL · Redis', 'Stockholm', 3, @now - INTERVAL 210 DAY, @now - INTERVAL 45 MINUTE),
 ('10000000-0000-0000-0000-000000000311', 'Frontend engineer · React · design systems', 'Singapore', 3, @now - INTERVAL 200 DAY, @now - INTERVAL 40 MINUTE),
 ('10000000-0000-0000-0000-000000000312', 'Full-stack engineer · React · Node · GraphQL', 'Milan', 2, @now - INTERVAL 190 DAY, @now - INTERVAL 35 MINUTE),
 ('10000000-0000-0000-0000-000000000313', 'Android engineer · Kotlin · KMP', 'Shanghai', 4, @now - INTERVAL 180 DAY, @now - INTERVAL 30 MINUTE),
 ('10000000-0000-0000-0000-000000000314', 'QA automation · Playwright · Cypress', 'Cairo', 2, @now - INTERVAL 170 DAY, @now - INTERVAL 25 MINUTE),
 ('10000000-0000-0000-0000-000000000315', 'Backend engineer · Go · gRPC', 'Hamburg', 3, @now - INTERVAL 160 DAY, @now - INTERVAL 20 MINUTE),
 ('10000000-0000-0000-0000-000000000316', 'BI analyst · SQL · Looker · dbt', 'Bucharest', 2, @now - INTERVAL 150 DAY, @now - INTERVAL 18 MINUTE),
 ('10000000-0000-0000-0000-000000000317', 'Technical product manager', 'New York', 4, @now - INTERVAL 140 DAY, @now - INTERVAL 16 MINUTE),
 ('10000000-0000-0000-0000-000000000318', 'UX designer · Figma · research', 'Abuja', 2, @now - INTERVAL 130 DAY, @now - INTERVAL 14 MINUTE),
 ('10000000-0000-0000-0000-000000000319', 'DevOps engineer · AWS · GitHub Actions', 'Barcelona', 3, @now - INTERVAL 120 DAY, @now - INTERVAL 12 MINUTE),
 ('10000000-0000-0000-0000-000000000320', 'AI/LLM engineer · LangChain · vector databases', 'Dublin', 3, @now - INTERVAL 110 DAY, @now - INTERVAL 10 MINUTE);

INSERT IGNORE INTO resumes
    (id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     version, created_at, updated_at, skills_json)
VALUES
 ('50000000-0000-0000-0000-000000000301', '10000000-0000-0000-0000-000000000301',
  'Lena Hoffmann', 30, 'Berlin', 'Data engineer · Spark · Airflow · dbt',
  'Data engineer building batch and streaming pipelines for a marketplace with 40M users. Owns data quality end to end and introduced dbt testing that cut broken dashboards by half.',
  '[{"experienceId":"exp-lena-1","title":"Senior Data Engineer","company":"Shoplytics","description":"Built Spark and Airflow pipelines processing 2TB daily. Led a dbt adoption across four squads and cut nightly job failures by 60 percent.","startDate":"2021-03","endDate":null}]',
  3, @now - INTERVAL 295 DAY, @now - INTERVAL 90 MINUTE,
  '["Spark","Airflow","dbt","Kafka","Python","SQL","Redshift"]'),
 ('50000000-0000-0000-0000-000000000302', '10000000-0000-0000-0000-000000000302',
  'Arjun Mehta', 28, 'Bangalore', 'ML/NLP engineer · PyTorch · Transformers',
  'ML engineer focused on NLP and ranking. Shipped intent classification and semantic search for a support platform, improving self-serve resolution by 25 percent.',
  '[{"experienceId":"exp-arjun-1","title":"ML Engineer","company":"HelpDesk AI","description":"Trained and served BERT-based intent classifiers and a semantic search index with PyTorch and FAISS. Built the offline eval suite used for every model release.","startDate":"2020-07","endDate":null}]',
  4, @now - INTERVAL 285 DAY, @now - INTERVAL 85 MINUTE,
  '["Python","PyTorch","Transformers","FAISS","FastAPI","MLOps"]'),
 ('50000000-0000-0000-0000-000000000303', '10000000-0000-0000-0000-000000000303',
  'Chloe Dubois', 27, 'Paris', 'iOS engineer · Swift · SwiftUI',
  'iOS engineer with four years shipping a banking app used by 2M customers. Strong on SwiftUI, accessibility, and release automation with Fastlane.',
  '[{"experienceId":"exp-chloe-1","title":"iOS Engineer","company":"Finbank","description":"Built account and payments flows in SwiftUI and automated the TestFlight release pipeline with Fastlane. Cut crash-free sessions below 99.9 percent target with a structured QA loop.","startDate":"2021-01","endDate":null}]',
  2, @now - INTERVAL 275 DAY, @now - INTERVAL 80 MINUTE,
  '["Swift","SwiftUI","Combine","Fastlane","XCTest","Core Data"]'),
 ('50000000-0000-0000-0000-000000000304', '10000000-0000-0000-0000-000000000304',
  'Mateo Garcia', 32, 'Madrid', 'Security engineer · AppSec · OWASP',
  'Application security engineer running SAST/DAST pipelines and threat modelling for a fintech. Halved time-to-fix for critical findings through triage automation.',
  '[{"experienceId":"exp-mateo-1","title":"Security Engineer","company":"PaySecure","description":"Owned the AppSec program: SAST/DAST in CI, threat models for new features, and a security champion program across 40 engineers.","startDate":"2019-02","endDate":null}]',
  3, @now - INTERVAL 265 DAY, @now - INTERVAL 75 MINUTE,
  '["AppSec","OWASP","Go","Burp Suite","SAST","DAST","Threat Modeling"]'),
 ('50000000-0000-0000-0000-000000000305', '10000000-0000-0000-0000-000000000305',
  'Hana Kim', 33, 'Seoul', 'Embedded engineer · C · Rust · RTOS',
  'Firmware engineer for automotive ECUs. Experienced with RTOS scheduling, CAN protocols, and rewriting hot paths in Rust for memory safety.',
  '[{"experienceId":"exp-hana-1","title":"Senior Firmware Engineer","company":"AutoLink","description":"Developed body-control ECU firmware in C on FreeRTOS, and led a pilot port of a safety module to Rust with zero memory violations in production.","startDate":"2017-09","endDate":null}]',
  5, @now - INTERVAL 255 DAY, @now - INTERVAL 70 MINUTE,
  '["C","Rust","FreeRTOS","CAN","Embedded Linux","JTAG"]'),
 ('50000000-0000-0000-0000-000000000306', '10000000-0000-0000-0000-000000000306',
  'Oliver Smith', 26, 'London', 'Game developer · Unity · C#',
  'Gameplay programmer with two shipped mobile titles totalling 10M downloads. Focused on performance profiling, shaders, and live-ops tooling.',
  '[{"experienceId":"exp-oliver-1","title":"Gameplay Programmer","company":"Pixel Forge","description":"Shipped two Unity mobile titles and built the live-ops event framework. Profiled and optimised draw calls to hold 60fps on mid-range devices.","startDate":"2022-04","endDate":null}]',
  2, @now - INTERVAL 245 DAY, @now - INTERVAL 65 MINUTE,
  '["Unity","C#","ShaderLab","Profiling","Game Design","Git"]'),
 ('50000000-0000-0000-0000-000000000307', '10000000-0000-0000-0000-000000000307',
  'Yuki Tanaka', 29, 'Tokyo', 'Blockchain engineer · Solidity · EVM',
  'Smart contract engineer who shipped audited DeFi contracts with 200M USD TVL. Focused on security patterns, gas optimisation, and formal verification tooling.',
  '[{"experienceId":"exp-yuki-1","title":"Smart Contract Engineer","company":"ChainLayer","description":"Developed and audited lending protocol contracts in Solidity. Introduced invariant fuzzing with Foundry that caught three critical bugs pre-audit.","startDate":"2020-10","endDate":null}]',
  3, @now - INTERVAL 235 DAY, @now - INTERVAL 60 MINUTE,
  '["Solidity","EVM","Foundry","Hardhat","TypeScript","DeFi"]'),
 ('50000000-0000-0000-0000-000000000308', '10000000-0000-0000-0000-000000000308',
  'Amara Osei', 31, 'Lagos', 'SRE · Kubernetes · Prometheus',
  'SRE running a fintech platform on Kubernetes across three regions. Drove SLO adoption and reduced incident noise with alert tuning and runbooks.',
  '[{"experienceId":"exp-amara-1","title":"Site Reliability Engineer","company":"PayBridge","description":"Operated 200+ microservices on EKS. Built the SLO framework, cut paging by 45 percent, and led two cross-region failover drills per quarter.","startDate":"2019-06","endDate":null}]',
  4, @now - INTERVAL 225 DAY, @now - INTERVAL 55 MINUTE,
  '["Kubernetes","Prometheus","Grafana","Terraform","Go","SLOs"]'),
 ('50000000-0000-0000-0000-000000000309', '10000000-0000-0000-0000-000000000309',
  'Lucas Silva', 36, 'Sao Paulo', 'Cloud architect · AWS · GCP',
  'Cloud architect who led a hybrid-cloud migration for a 500-person retailer. Designs landing zones, cost governance, and platform teams.',
  '[{"experienceId":"exp-lucas-1","title":"Cloud Architect","company":"RetailCore","description":"Designed the AWS landing zone and led migration of 120 workloads from on-prem. Introduced cost tagging that reduced cloud spend by 28 percent.","startDate":"2016-04","endDate":null}]',
  5, @now - INTERVAL 215 DAY, @now - INTERVAL 50 MINUTE,
  '["AWS","GCP","Terraform","Landing Zones","FinOps","Architecture"]'),
 ('50000000-0000-0000-0000-000000000310', '10000000-0000-0000-0000-000000000310',
  'Emma Larsson', 30, 'Stockholm', 'Database engineer · PostgreSQL · Redis',
  'Database engineer specialised in PostgreSQL tuning and migrations for a gaming platform. Writes the runbooks that keep 2TB databases boring.',
  '[{"experienceId":"exp-emma-1","title":"Database Engineer","company":"Nordic Games","description":"Managed 40 PostgreSQL clusters and led a zero-downtime major-version upgrade. Cut p95 query latency by 40 percent through index and vacuum tuning.","startDate":"2020-01","endDate":null}]',
  3, @now - INTERVAL 205 DAY, @now - INTERVAL 45 MINUTE,
  '["PostgreSQL","Redis","pgBouncer","SQL","Terraform","Monitoring"]'),
 ('50000000-0000-0000-0000-000000000311', '10000000-0000-0000-0000-000000000311',
  'Rahul Nair', 27, 'Singapore', 'Frontend engineer · React · design systems',
  'Frontend engineer building design systems and analytics dashboards in React and TypeScript. Cares about accessibility and bundle budgets.',
  '[{"experienceId":"exp-rahul-1","title":"Frontend Engineer","company":"DataScope","description":"Built the customer analytics dashboard in React and maintained the shared design system used by six teams. Reduced bundle size by 35 percent with code splitting.","startDate":"2021-08","endDate":null}]',
  3, @now - INTERVAL 195 DAY, @now - INTERVAL 40 MINUTE,
  '["React","TypeScript","Design Systems","Vite","Storybook","Accessibility"]'),
 ('50000000-0000-0000-0000-000000000312', '10000000-0000-0000-0000-000000000312',
  'Isabella Rossi', 26, 'Milan', 'Full-stack engineer · React · Node · GraphQL',
  'Full-stack engineer shipping features across React, Node.js and GraphQL for an e-commerce startup. Comfortable from schema design to CSS.',
  '[{"experienceId":"exp-isabella-1","title":"Full-stack Engineer","company":"ModaCart","description":"Owned checkout and order tracking end to end with React, Node.js and GraphQL. Cut checkout abandonment by 18 percent with a UX and API rework.","startDate":"2022-02","endDate":null}]',
  2, @now - INTERVAL 185 DAY, @now - INTERVAL 35 MINUTE,
  '["React","Node.js","GraphQL","TypeScript","PostgreSQL","CSS"]'),
 ('50000000-0000-0000-0000-000000000313', '10000000-0000-0000-0000-000000000313',
  'Wei Chen', 29, 'Shanghai', 'Android engineer · Kotlin · KMP',
  'Android engineer with five years on social and commerce apps. Leads a Kotlin Multiplatform adoption for shared business logic.',
  '[{"experienceId":"exp-wei-1","title":"Android Engineer","company":"ShopGo","description":"Built order and payment modules and led a KMP migration sharing networking logic with iOS. Maintains 99.5 percent crash-free sessions on 20M installs.","startDate":"2020-05","endDate":null}]',
  4, @now - INTERVAL 175 DAY, @now - INTERVAL 30 MINUTE,
  '["Kotlin","Jetpack Compose","KMP","Coroutines","Room","CI"]'),
 ('50000000-0000-0000-0000-000000000314', '10000000-0000-0000-0000-000000000314',
  'Nadia Hassan', 28, 'Cairo', 'QA automation · Playwright · Cypress',
  'QA automation engineer who built the E2E suite covering checkout for a retail platform. Believes flaky tests are bugs and fixes them at the source.',
  '[{"experienceId":"exp-nadia-1","title":"QA Automation Engineer","company":"NileMart","description":"Built Playwright suites for web and mobile web covering checkout, and cut release-blocking defects by 70 percent with parallel CI runs.","startDate":"2021-04","endDate":null}]',
  2, @now - INTERVAL 165 DAY, @now - INTERVAL 25 MINUTE,
  '["Playwright","Cypress","TypeScript","API Testing","CI/CD","Allure"]'),
 ('50000000-0000-0000-0000-000000000315', '10000000-0000-0000-0000-000000000315',
  'Tom Becker', 31, 'Hamburg', 'Backend engineer · Go · gRPC',
  'Backend engineer building Go microservices for a logistics platform. Focused on gRPC APIs, event sourcing, and clean testing strategies.',
  '[{"experienceId":"exp-tom-1","title":"Backend Engineer","company":"ShipFast","description":"Designed gRPC services for shipment tracking and introduced event-sourced order state. Cut integration test runtime from 20 to 6 minutes.","startDate":"2019-11","endDate":null}]',
  3, @now - INTERVAL 155 DAY, @now - INTERVAL 20 MINUTE,
  '["Go","gRPC","Kafka","Event Sourcing","PostgreSQL","Docker"]'),
 ('50000000-0000-0000-0000-000000000316', '10000000-0000-0000-0000-000000000316',
  'Sofia Popescu', 25, 'Bucharest', 'BI analyst · SQL · Looker · dbt',
  'BI analyst turning product funnels into Looker dashboards for leadership. Owns the dbt models that power weekly business reviews.',
  '[{"experienceId":"exp-sofia-p-1","title":"BI Analyst","company":"Metrica","description":"Built Looker dashboards for product and finance and migrated 200 reports onto dbt models with documented lineage.","startDate":"2022-09","endDate":null}]',
  2, @now - INTERVAL 145 DAY, @now - INTERVAL 18 MINUTE,
  '["SQL","Looker","dbt","Python","A/B Testing","Excel"]'),
 ('50000000-0000-0000-0000-000000000317', '10000000-0000-0000-0000-000000000317',
  'Jack Thompson', 34, 'New York', 'Technical product manager',
  'Technical product manager with eight years shipping developer tools and recruiting products. Writes specs engineers can build from.',
  '[{"experienceId":"exp-jack-1","title":"Product Manager","company":"DevTools Inc","description":"Led the roadmap for a CI analytics product from zero to 40k MAU, running discovery, pricing, and cross-functional delivery.","startDate":"2018-03","endDate":null}]',
  4, @now - INTERVAL 135 DAY, @now - INTERVAL 16 MINUTE,
  '["Product Strategy","Roadmapping","SQL","Analytics","User Research","APIs"]'),
 ('50000000-0000-0000-0000-000000000318', '10000000-0000-0000-0000-000000000318',
  'Aisha Bello', 27, 'Abuja', 'UX designer · Figma · research',
  'UX designer for fintech mobile apps. Runs discovery interviews, prototypes in Figma, and pairs with engineering through handoff.',
  '[{"experienceId":"exp-aisha-1","title":"UX Designer","company":"KudaPay","description":"Redesigned the onboarding flow, raising completion from 55 to 82 percent. Established the research repository used by all squads.","startDate":"2021-06","endDate":null}]',
  2, @now - INTERVAL 125 DAY, @now - INTERVAL 14 MINUTE,
  '["Figma","User Research","Prototyping","Design Systems","Usability Testing"]'),
 ('50000000-0000-0000-0000-000000000319', '10000000-0000-0000-0000-000000000319',
  'Miguel Torres', 30, 'Barcelona', 'DevOps engineer · AWS · GitHub Actions',
  'DevOps engineer automating release pipelines for a SaaS with 300 daily deploys. Platform as code, everything reviewed.',
  '[{"experienceId":"exp-miguel-1","title":"DevOps Engineer","company":"CloudDesk","description":"Rebuilt the release pipeline on GitHub Actions with blue-green deploys, cutting lead time from 2 days to 40 minutes.","startDate":"2020-02","endDate":null}]',
  3, @now - INTERVAL 115 DAY, @now - INTERVAL 12 MINUTE,
  '["AWS","Kubernetes","GitHub Actions","Terraform","ArgoCD","Bash"]'),
 ('50000000-0000-0000-0000-000000000320', '10000000-0000-0000-0000-000000000320',
  'Fiona Gallagher', 29, 'Dublin', 'AI/LLM engineer · LangChain · vector databases',
  'LLM engineer building RAG assistants for a support platform. Focused on evaluation pipelines, retrieval quality, and safe tool use.',
  '[{"experienceId":"exp-fiona-1","title":"LLM Engineer","company":"SupportAI","description":"Built a RAG assistant answering 60 percent of tier-1 tickets. Designed the eval harness comparing retrieval strategies offline before every rollout.","startDate":"2021-11","endDate":null}]',
  3, @now - INTERVAL 105 DAY, @now - INTERVAL 10 MINUTE,
  '["Python","LangChain","Pinecone","OpenAI API","RAG","Evaluation"]');

-- Resume snapshots (captured at application time)
INSERT IGNORE INTO resume_snapshots
    (id, resume_id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     resume_version, resume_created_at, resume_updated_at, captured_at, skills_json)
SELECT '60000000-0000-0000-0000-000000000301', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 12 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000301'
UNION ALL SELECT '60000000-0000-0000-0000-000000000302', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 11 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000302'
UNION ALL SELECT '60000000-0000-0000-0000-000000000303', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 10 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000303'
UNION ALL SELECT '60000000-0000-0000-0000-000000000304', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 9 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000304'
UNION ALL SELECT '60000000-0000-0000-0000-000000000305', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 9 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000305'
UNION ALL SELECT '60000000-0000-0000-0000-000000000306', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 8 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000306'
UNION ALL SELECT '60000000-0000-0000-0000-000000000307', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 8 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000307'
UNION ALL SELECT '60000000-0000-0000-0000-000000000308', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 7 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000308'
UNION ALL SELECT '60000000-0000-0000-0000-000000000309', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 7 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000309'
UNION ALL SELECT '60000000-0000-0000-0000-000000000310', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 6 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000310'
UNION ALL SELECT '60000000-0000-0000-0000-000000000311', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 6 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000311'
UNION ALL SELECT '60000000-0000-0000-0000-000000000312', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 5 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000312'
UNION ALL SELECT '60000000-0000-0000-0000-000000000313', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 5 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000313'
UNION ALL SELECT '60000000-0000-0000-0000-000000000314', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 4 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000314'
UNION ALL SELECT '60000000-0000-0000-0000-000000000315', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 4 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000315'
UNION ALL SELECT '60000000-0000-0000-0000-000000000316', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 3 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000316'
UNION ALL SELECT '60000000-0000-0000-0000-000000000317', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 3 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000317'
UNION ALL SELECT '60000000-0000-0000-0000-000000000318', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 2 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000318'
UNION ALL SELECT '60000000-0000-0000-0000-000000000319', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 2 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000319'
UNION ALL SELECT '60000000-0000-0000-0000-000000000320', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at, @now - INTERVAL 1 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000320';

INSERT IGNORE INTO applications
    (id, job_id, candidate_id, resume_id, resume_snapshot_id, contact_email, share_profile,
     status, applied_at, updated_at, version)
VALUES
 ('70000000-0000-0000-0000-000000000301', '40000000-0000-0000-0000-000000000006',
  '10000000-0000-0000-0000-000000000301', '50000000-0000-0000-0000-000000000301',
  '60000000-0000-0000-0000-000000000301', 'lena@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 12 DAY, @now - INTERVAL 1 DAY, 2),
 ('70000000-0000-0000-0000-000000000302', '40000000-0000-0000-0000-000000000011',
  '10000000-0000-0000-0000-000000000302', '50000000-0000-0000-0000-000000000302',
  '60000000-0000-0000-0000-000000000302', 'arjun@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 11 DAY, @now - INTERVAL 2 HOUR, 3),
 ('70000000-0000-0000-0000-000000000303', '40000000-0000-0000-0000-000000000015',
  '10000000-0000-0000-0000-000000000303', '50000000-0000-0000-0000-000000000303',
  '60000000-0000-0000-0000-000000000303', 'chloe@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 10 DAY, @now - INTERVAL 4 HOUR, 2),
 ('70000000-0000-0000-0000-000000000304', '40000000-0000-0000-0000-000000000004',
  '10000000-0000-0000-0000-000000000304', '50000000-0000-0000-0000-000000000304',
  '60000000-0000-0000-0000-000000000304', 'mateo@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 9 DAY, @now - INTERVAL 6 HOUR, 2),
 ('70000000-0000-0000-0000-000000000305', '40000000-0000-0000-0000-000000000008',
  '10000000-0000-0000-0000-000000000305', '50000000-0000-0000-0000-000000000305',
  '60000000-0000-0000-0000-000000000305', 'hana@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 9 DAY, @now - INTERVAL 8 HOUR, 2),
 ('70000000-0000-0000-0000-000000000306', '40000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000306', '50000000-0000-0000-0000-000000000306',
  '60000000-0000-0000-0000-000000000306', 'oliver@demo.local', TRUE,
  'APPLIED', @now - INTERVAL 8 DAY, @now - INTERVAL 8 DAY, 1),
 ('70000000-0000-0000-0000-000000000307', '40000000-0000-0000-0000-000000000006',
  '10000000-0000-0000-0000-000000000307', '50000000-0000-0000-0000-000000000307',
  '60000000-0000-0000-0000-000000000307', 'yuki@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 8 DAY, @now - INTERVAL 10 HOUR, 2),
 ('70000000-0000-0000-0000-000000000308', '40000000-0000-0000-0000-000000000008',
  '10000000-0000-0000-0000-000000000308', '50000000-0000-0000-0000-000000000308',
  '60000000-0000-0000-0000-000000000308', 'amara@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 7 DAY, @now - INTERVAL 1 DAY, 3),
 ('70000000-0000-0000-0000-000000000309', '40000000-0000-0000-0000-000000000014',
  '10000000-0000-0000-0000-000000000309', '50000000-0000-0000-0000-000000000309',
  '60000000-0000-0000-0000-000000000309', 'lucas@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 7 DAY, @now - INTERVAL 12 HOUR, 2),
 ('70000000-0000-0000-0000-000000000310', '40000000-0000-0000-0000-000000000006',
  '10000000-0000-0000-0000-000000000310', '50000000-0000-0000-0000-000000000310',
  '60000000-0000-0000-0000-000000000310', 'emma@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 6 DAY, @now - INTERVAL 14 HOUR, 2),
 ('70000000-0000-0000-0000-000000000311', '40000000-0000-0000-0000-000000000007',
  '10000000-0000-0000-0000-000000000311', '50000000-0000-0000-0000-000000000311',
  '60000000-0000-0000-0000-000000000311', 'rahul@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 6 DAY, @now - INTERVAL 16 HOUR, 3),
 ('70000000-0000-0000-0000-000000000312', '40000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000312', '50000000-0000-0000-0000-000000000312',
  '60000000-0000-0000-0000-000000000312', 'isabella@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 5 DAY, @now - INTERVAL 18 HOUR, 2),
 ('70000000-0000-0000-0000-000000000313', '40000000-0000-0000-0000-000000000002',
  '10000000-0000-0000-0000-000000000313', '50000000-0000-0000-0000-000000000313',
  '60000000-0000-0000-0000-000000000313', 'wei@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 5 DAY, @now - INTERVAL 20 HOUR, 2),
 ('70000000-0000-0000-0000-000000000314', '40000000-0000-0000-0000-000000000010',
  '10000000-0000-0000-0000-000000000314', '50000000-0000-0000-0000-000000000314',
  '60000000-0000-0000-0000-000000000314', 'nadia@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 4 DAY, @now - INTERVAL 22 HOUR, 2),
 ('70000000-0000-0000-0000-000000000315', '40000000-0000-0000-0000-000000000006',
  '10000000-0000-0000-0000-000000000315', '50000000-0000-0000-0000-000000000315',
  '60000000-0000-0000-0000-000000000315', 'tom@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 4 DAY, @now - INTERVAL 24 HOUR, 2),
 ('70000000-0000-0000-0000-000000000316', '40000000-0000-0000-0000-000000000009',
  '10000000-0000-0000-0000-000000000316', '50000000-0000-0000-0000-000000000316',
  '60000000-0000-0000-0000-000000000316', 'sofia-p@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 3 DAY, @now - INTERVAL 26 HOUR, 2),
 ('70000000-0000-0000-0000-000000000317', '40000000-0000-0000-0000-000000000005',
  '10000000-0000-0000-0000-000000000317', '50000000-0000-0000-0000-000000000317',
  '60000000-0000-0000-0000-000000000317', 'jack@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 3 DAY, @now - INTERVAL 28 HOUR, 2),
 ('70000000-0000-0000-0000-000000000318', '40000000-0000-0000-0000-000000000005',
  '10000000-0000-0000-0000-000000000318', '50000000-0000-0000-0000-000000000318',
  '60000000-0000-0000-0000-000000000318', 'aisha@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 2 DAY, @now - INTERVAL 30 HOUR, 3),
 ('70000000-0000-0000-0000-000000000319', '40000000-0000-0000-0000-000000000008',
  '10000000-0000-0000-0000-000000000319', '50000000-0000-0000-0000-000000000319',
  '60000000-0000-0000-0000-000000000319', 'miguel@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 2 DAY, @now - INTERVAL 32 HOUR, 2),
 ('70000000-0000-0000-0000-000000000320', '40000000-0000-0000-0000-000000000011',
  '10000000-0000-0000-0000-000000000320', '50000000-0000-0000-0000-000000000320',
  '60000000-0000-0000-0000-000000000320', 'fiona@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 1 DAY, @now - INTERVAL 34 HOUR, 3);

-- Keep applicant_count correct no matter when this script runs.
UPDATE jobs j
SET applicant_count = (SELECT COUNT(*) FROM applications a WHERE a.job_id = j.id)
WHERE j.id IN ('40000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000002',
               '40000000-0000-0000-0000-000000000004','40000000-0000-0000-0000-000000000005',
               '40000000-0000-0000-0000-000000000006','40000000-0000-0000-0000-000000000007',
               '40000000-0000-0000-0000-000000000008','40000000-0000-0000-0000-000000000009',
               '40000000-0000-0000-0000-000000000010','40000000-0000-0000-0000-000000000011',
               '40000000-0000-0000-0000-000000000014','40000000-0000-0000-0000-000000000015');
