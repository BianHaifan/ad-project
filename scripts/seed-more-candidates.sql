-- ---------------------------------------------------------------------------
-- Demo candidates batch 2: ten realistic candidates with diverse tech stacks.
-- Non-destructive: plain INSERTs only, safe to run once against a fresh or an
-- already-seeded demo DB (applicant_count is recomputed, so it stays correct).
-- All candidates sign in with the shared demo password.
-- ---------------------------------------------------------------------------

SET @password_hash = '$2a$12$AnAsV/cb78zYQshjiKBkHOI1An3SXamixKMaqPQL4hWudJv8xNyem';
SET @now = UTC_TIMESTAMP(6);

INSERT INTO users (id, email, password_hash, full_name, role, status, accepted_terms_version, avatar_url, created_at, updated_at) VALUES
 ('10000000-0000-0000-0000-000000000201', 'dmitri@demo.local',    @password_hash, 'Dmitri Volkov',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 400 DAY, @now - INTERVAL 90 MINUTE),
 ('10000000-0000-0000-0000-000000000202', 'mei@demo.local',       @password_hash, 'Mei Lin',         'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 320 DAY, @now - INTERVAL 80 MINUTE),
 ('10000000-0000-0000-0000-000000000203', 'sofia@demo.local',     @password_hash, 'Sofia Rossi',     'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 350 DAY, @now - INTERVAL 70 MINUTE),
 ('10000000-0000-0000-0000-000000000204', 'takeshi@demo.local',   @password_hash, 'Takeshi Sato',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 500 DAY, @now - INTERVAL 60 MINUTE),
 ('10000000-0000-0000-0000-000000000205', 'priya@demo.local',     @password_hash, 'Priya Sharma',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 280 DAY, @now - INTERVAL 50 MINUTE),
 ('10000000-0000-0000-0000-000000000206', 'carlos@demo.local',    @password_hash, 'Carlos Mendes',   'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 450 DAY, @now - INTERVAL 40 MINUTE),
 ('10000000-0000-0000-0000-000000000207', 'fatima@demo.local',    @password_hash, 'Fatima Al-Sayed', 'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 300 DAY, @now - INTERVAL 30 MINUTE),
 ('10000000-0000-0000-0000-000000000208', 'jonas@demo.local',     @password_hash, 'Jonas Weber',     'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 520 DAY, @now - INTERVAL 25 MINUTE),
 ('10000000-0000-0000-0000-000000000209', 'ananya@demo.local',    @password_hash, 'Ananya Rao',      'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 380 DAY, @now - INTERVAL 20 MINUTE),
 ('10000000-0000-0000-0000-000000000210', 'grace@demo.local',     @password_hash, 'Grace Okafor',    'CANDIDATE', 'ACTIVE', '2026-01', NULL, @now - INTERVAL 150 DAY, @now - INTERVAL 15 MINUTE);

INSERT INTO candidate_profiles (user_id, headline, location, version, created_at, updated_at) VALUES
 ('10000000-0000-0000-0000-000000000201', 'Backend engineer · Java · Spring Boot · Kafka', 'Singapore', 3, @now - INTERVAL 400 DAY, @now - INTERVAL 90 MINUTE),
 ('10000000-0000-0000-0000-000000000202', 'Frontend engineer · React · Next.js', 'Shanghai', 2, @now - INTERVAL 320 DAY, @now - INTERVAL 80 MINUTE),
 ('10000000-0000-0000-0000-000000000203', 'Full-stack engineer · React · Node.js', 'Singapore', 4, @now - INTERVAL 350 DAY, @now - INTERVAL 70 MINUTE),
 ('10000000-0000-0000-0000-000000000204', 'Android engineer · Kotlin · Jetpack Compose', 'Tokyo', 5, @now - INTERVAL 500 DAY, @now - INTERVAL 60 MINUTE),
 ('10000000-0000-0000-0000-000000000205', 'Data analyst · SQL · Python · Tableau', 'Bangalore', 2, @now - INTERVAL 280 DAY, @now - INTERVAL 50 MINUTE),
 ('10000000-0000-0000-0000-000000000206', 'DevOps engineer · AWS · Terraform · Kubernetes', 'Sao Paulo', 3, @now - INTERVAL 450 DAY, @now - INTERVAL 40 MINUTE),
 ('10000000-0000-0000-0000-000000000207', 'ML engineer · PyTorch · NLP', 'Dubai', 2, @now - INTERVAL 300 DAY, @now - INTERVAL 30 MINUTE),
 ('10000000-0000-0000-0000-000000000208', 'QA automation engineer · Playwright · API testing', 'Berlin', 4, @now - INTERVAL 520 DAY, @now - INTERVAL 25 MINUTE),
 ('10000000-0000-0000-0000-000000000209', 'Product designer · Figma · UX research', 'Singapore', 3, @now - INTERVAL 380 DAY, @now - INTERVAL 20 MINUTE),
 ('10000000-0000-0000-0000-000000000210', 'Customer success intern · CRM · support tooling', 'Lagos', 1, @now - INTERVAL 150 DAY, @now - INTERVAL 15 MINUTE);

INSERT INTO resumes
    (id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     version, created_at, updated_at, skills_json)
VALUES
 ('50000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000201',
  'Dmitri Volkov', 29, 'Singapore', 'Backend engineer · Java · Spring Boot · Kafka',
  'Backend engineer with six years of experience building high-throughput payment services in Java. Comfortable owning systems end to end: event-driven design, schema evolution, and production incident response. Mentors junior engineers on code review and reliability practices.',
  '[{"experienceId":"exp-dmitri-1","title":"Senior Backend Engineer","company":"Payflow","description":"Designed event-driven payment settlement services with Spring Boot and Kafka, processing 2M daily events. Led a PostgreSQL partitioning migration that cut query latency by 40 percent.","startDate":"2021-05","endDate":null},{"experienceId":"exp-dmitri-2","title":"Backend Engineer","company":"DataStream","description":"Built REST APIs and batch jobs for a streaming analytics platform. Introduced Redis caching and Docker-based local development.","startDate":"2018-07","endDate":"2021-04"}]',
  3, @now - INTERVAL 395 DAY, @now - INTERVAL 90 MINUTE,
  '["Java","Spring Boot","Kafka","PostgreSQL","Redis","Docker","gRPC"]'),
 ('50000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000202',
  'Mei Lin', 26, 'Shanghai', 'Frontend engineer · React · Next.js',
  'Frontend engineer focused on design systems and web performance. Three years building customer-facing storefronts with React and Next.js, with a track record of shipping accessible components used by multiple product teams.',
  '[{"experienceId":"exp-mei-1","title":"Frontend Engineer","company":"ShopWave","description":"Built the storefront in React and Next.js and led a design system adoption across three teams. Cut Largest Contentful Paint by 35 percent through code splitting and image optimization.","startDate":"2021-09","endDate":null},{"experienceId":"exp-mei-2","title":"Frontend Intern","company":"PixelWorks","description":"Shipped landing pages and reusable UI components in React with Jest coverage.","startDate":"2020-06","endDate":"2021-08"}]',
  2, @now - INTERVAL 315 DAY, @now - INTERVAL 80 MINUTE,
  '["React","TypeScript","Next.js","CSS","Design Systems","Jest","Storybook"]'),
 ('50000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000203',
  'Sofia Rossi', 28, 'Singapore', 'Full-stack engineer · React · Node.js',
  'Product-minded full-stack engineer who ships features across the whole stack: React frontends, Node.js APIs, and Postgres data models. Five years of startup experience, from MVP to scale, with a focus on delivery speed and clean handovers.',
  '[{"experienceId":"exp-sofia-1","title":"Full-stack Engineer","company":"Meridian Health","description":"Owned the patient portal feature area end to end: React UI, Node.js services, and PostgreSQL schema design. Introduced GraphQL federation and reduced release cycle time by 30 percent.","startDate":"2020-02","endDate":null},{"experienceId":"exp-sofia-2","title":"Software Engineer","company":"BrightCart","description":"Built merchant dashboards and checkout flows in React with AWS Lambda backends.","startDate":"2018-06","endDate":"2020-01"}]',
  4, @now - INTERVAL 345 DAY, @now - INTERVAL 70 MINUTE,
  '["React","Node.js","TypeScript","PostgreSQL","GraphQL","Docker","AWS"]'),
 ('50000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000204',
  'Takeshi Sato', 31, 'Tokyo', 'Android engineer · Kotlin · Jetpack Compose',
  'Android engineer with eight years on the platform. Deep experience migrating legacy views to Jetpack Compose, improving startup performance, and maintaining a 4.6-star app used by 5M riders. Values readable architecture and thorough instrumentation.',
  '[{"experienceId":"exp-takeshi-1","title":"Staff Android Engineer","company":"CityRide","description":"Led a full Compose migration of the rider app. Cut cold start from 3.2s to 1.4s and introduced coroutine-first networking with Retrofit. Runs the Android guild for 12 engineers.","startDate":"2018-04","endDate":null},{"experienceId":"exp-takeshi-2","title":"Android Developer","company":"Kumo Games","description":"Built social features for a mobile game with 2M installs using Kotlin and Firebase.","startDate":"2015-06","endDate":"2018-03"}]',
  5, @now - INTERVAL 495 DAY, @now - INTERVAL 60 MINUTE,
  '["Kotlin","Jetpack Compose","Coroutines","Room","Firebase","Retrofit","MVVM","CI/CD"]'),
 ('50000000-0000-0000-0000-000000000205', '10000000-0000-0000-0000-000000000205',
  'Priya Sharma', 25, 'Bangalore', 'Data analyst · SQL · Python · Tableau',
  'Data analyst with three years turning messy operational data into dashboards and decisions. Strong SQL and Python for pipelines, comfortable presenting findings to executives. Experience with dbt transformations and A/B test analysis.',
  '[{"experienceId":"exp-priya-1","title":"Data Analyst","company":"RetailKart","description":"Built company-wide KPI dashboards in Tableau used by 200 employees. Wrote dbt models and Python scripts that reduced weekly reporting effort from two days to three hours.","startDate":"2022-01","endDate":null},{"experienceId":"exp-priya-2","title":"Junior Data Analyst","company":"FinLens","description":"Cleaned and analyzed loan portfolio data with SQL and Excel; supported monthly investor reports.","startDate":"2020-08","endDate":"2021-12"}]',
  2, @now - INTERVAL 275 DAY, @now - INTERVAL 50 MINUTE,
  '["SQL","Python","Pandas","Tableau","dbt","Excel","A/B Testing"]'),
 ('50000000-0000-0000-0000-000000000206', '10000000-0000-0000-0000-000000000206',
  'Carlos Mendes', 30, 'Sao Paulo', 'DevOps engineer · AWS · Terraform · Kubernetes',
  'Platform engineer who treats infrastructure as a product. Seven years automating AWS environments with Terraform and Kubernetes, building self-service pipelines, and driving SRE practices. Reduced cloud spend by 25 percent at his current company.',
  '[{"experienceId":"exp-carlos-1","title":"DevOps Engineer","company":"LogiChain","description":"Owns the AWS platform: Terraform modules, EKS clusters, and GitHub Actions pipelines for 30 services. Built an autoscaling strategy that cut cloud spend by 25 percent.","startDate":"2019-03","endDate":null},{"experienceId":"exp-carlos-2","title":"Sysadmin","company":"HostBrasil","description":"Managed Linux servers, backups, and monitoring with Prometheus and Grafana for 400 customers.","startDate":"2015-09","endDate":"2019-02"}]',
  3, @now - INTERVAL 445 DAY, @now - INTERVAL 40 MINUTE,
  '["AWS","Terraform","Kubernetes","Docker","Go","Prometheus","GitHub Actions","Linux"]'),
 ('50000000-0000-0000-0000-000000000207', '10000000-0000-0000-0000-000000000207',
  'Fatima Al-Sayed', 27, 'Dubai', 'ML engineer · PyTorch · NLP',
  'Machine learning engineer specializing in NLP: fine-tuning, retrieval, and model serving. Five years shipping ML systems, including a document classifier that handles 10M documents a month. Cares about evaluation discipline and MLOps.',
  '[{"experienceId":"exp-fatima-1","title":"ML Engineer","company":"DocuMind AI","description":"Built and served a multilingual document classifier with PyTorch and transformers handling 10M documents monthly. Set up MLflow experiment tracking and automated evaluation.","startDate":"2021-02","endDate":null},{"experienceId":"exp-fatima-2","title":"Data Scientist","company":"Insight Analytics","description":"Delivered churn prediction models with scikit-learn and XGBoost; worked with clients to productionize scores.","startDate":"2018-09","endDate":"2021-01"}]',
  2, @now - INTERVAL 295 DAY, @now - INTERVAL 30 MINUTE,
  '["Python","PyTorch","TensorFlow","NLP","MLOps","scikit-learn","Transformers"]'),
 ('50000000-0000-0000-0000-000000000208', '10000000-0000-0000-0000-000000000208',
  'Jonas Weber', 32, 'Berlin', 'QA automation engineer · Playwright · API testing',
  'QA automation engineer who builds test frameworks rather than just test cases. Eight years covering web, API, and mobile testing; introduced contract testing and cut regression runtime by 60 percent. Believes quality is a team sport.',
  '[{"experienceId":"exp-jonas-1","title":"QA Automation Engineer","company":"FinGuard","description":"Built the Playwright-based end-to-end suite (900 scenarios) and REST contract tests with Python. Cut regression runtime by 60 percent through parallelization in CI.","startDate":"2018-05","endDate":null},{"experienceId":"exp-jonas-2","title":"QA Engineer","company":"ShopDesk","description":"Manual and automated testing for a retail platform; introduced Selenium coverage for checkout flows.","startDate":"2015-02","endDate":"2018-04"}]',
  4, @now - INTERVAL 515 DAY, @now - INTERVAL 25 MINUTE,
  '["Playwright","Selenium","Cypress","API Testing","Python","CI/CD","TestRail"]'),
 ('50000000-0000-0000-0000-000000000209', '10000000-0000-0000-0000-000000000209',
  'Ananya Rao', 29, 'Singapore', 'Product designer · Figma · UX research',
  'Product designer with six years across fintech and e-commerce. Leads discovery through delivery: user research, prototyping in Figma, and partnering with engineers on design systems. Shipped a redesign that raised conversion by 18 percent.',
  '[{"experienceId":"exp-ananya-1","title":"Senior Product Designer","company":"KayaPay","description":"Led the redesign of the payments dashboard; ran 30 user interviews and usability tests, shipped a Figma design system, and raised task completion by 22 percent.","startDate":"2020-01","endDate":null},{"experienceId":"exp-ananya-2","title":"UX Designer","company":"ShopLocal","description":"Designed checkout and loyalty features; introduced rapid prototyping workflow with the product team.","startDate":"2017-08","endDate":"2019-12"}]',
  3, @now - INTERVAL 375 DAY, @now - INTERVAL 20 MINUTE,
  '["Figma","UX Research","Prototyping","Design Systems","User Testing","Sketch"]'),
 ('50000000-0000-0000-0000-000000000210', '10000000-0000-0000-0000-000000000210',
  'Grace Okafor', 22, 'Lagos', 'Customer success intern · CRM · support tooling',
  'Final-year business student and part-time support agent. Two years helping customers on chat and email with a 94 percent satisfaction score. Learning SQL to analyze support tickets and fluent in HubSpot and Zendesk.',
  '[{"experienceId":"exp-grace-1","title":"Support Agent (part-time)","company":"SendRite","description":"Resolved 60-plus customer chats and emails weekly with a 94 percent satisfaction score. Maintained help-center articles in Zendesk that cut repeat tickets by 15 percent.","startDate":"2023-06","endDate":null},{"experienceId":"exp-grace-2","title":"Customer Service Intern","company":"AfriMart","description":"Handled order issues and refunds; logged cases in HubSpot and reported weekly resolution rates.","startDate":"2022-05","endDate":"2022-09"}]',
  1, @now - INTERVAL 145 DAY, @now - INTERVAL 15 MINUTE,
  '["HubSpot","Zendesk","CRM","Google Workspace","SQL (basic)","Communication"]');

-- One snapshot per application below (captured at the moment of applying).
INSERT INTO resume_snapshots
    (id, resume_id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     resume_version, resume_created_at, resume_updated_at, captured_at, skills_json)
SELECT '60000000-0000-0000-0000-000000000100', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 18 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000201'
UNION ALL SELECT '60000000-0000-0000-0000-000000000101', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 60 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000201'
UNION ALL SELECT '60000000-0000-0000-0000-000000000102', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 12 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000202'
UNION ALL SELECT '60000000-0000-0000-0000-000000000103', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 10 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000203'
UNION ALL SELECT '60000000-0000-0000-0000-000000000104', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 45 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000203'
UNION ALL SELECT '60000000-0000-0000-0000-000000000105', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 9 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000204'
UNION ALL SELECT '60000000-0000-0000-0000-000000000106', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 8 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000205'
UNION ALL SELECT '60000000-0000-0000-0000-000000000107', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 7 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000206'
UNION ALL SELECT '60000000-0000-0000-0000-000000000108', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 6 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000207'
UNION ALL SELECT '60000000-0000-0000-0000-000000000109', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 5 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000208'
UNION ALL SELECT '60000000-0000-0000-0000-000000000110', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 4 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000209'
UNION ALL SELECT '60000000-0000-0000-0000-000000000111', id, candidate_id, full_name, age, location, headline,
       summary, experiences_json, version, created_at, updated_at,
       @now - INTERVAL 3 DAY, skills_json
FROM resumes WHERE id = '50000000-0000-0000-0000-000000000210';

INSERT INTO applications
    (id, job_id, candidate_id, resume_id, resume_snapshot_id, contact_email, share_profile,
     status, applied_at, updated_at, version)
VALUES
 ('70000000-0000-0000-0000-000000000100', '40000000-0000-0000-0000-000000000006',
  '10000000-0000-0000-0000-000000000201', '50000000-0000-0000-0000-000000000201',
  '60000000-0000-0000-0000-000000000100', 'dmitri@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 18 DAY, @now - INTERVAL 2 DAY, 4),
 ('70000000-0000-0000-0000-000000000101', '40000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000201', '50000000-0000-0000-0000-000000000201',
  '60000000-0000-0000-0000-000000000101', 'dmitri@demo.local', TRUE,
  'REJECTED', @now - INTERVAL 60 DAY, @now - INTERVAL 40 DAY, 3),
 ('70000000-0000-0000-0000-000000000102', '40000000-0000-0000-0000-000000000007',
  '10000000-0000-0000-0000-000000000202', '50000000-0000-0000-0000-000000000202',
  '60000000-0000-0000-0000-000000000102', 'mei@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 12 DAY, @now - INTERVAL 1 DAY, 5),
 ('70000000-0000-0000-0000-000000000103', '40000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000203', '50000000-0000-0000-0000-000000000203',
  '60000000-0000-0000-0000-000000000103', 'sofia@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 10 DAY, @now - INTERVAL 6 HOUR, 3),
 ('70000000-0000-0000-0000-000000000104', '40000000-0000-0000-0000-000000000007',
  '10000000-0000-0000-0000-000000000203', '50000000-0000-0000-0000-000000000203',
  '60000000-0000-0000-0000-000000000104', 'sofia@demo.local', TRUE,
  'REJECTED', @now - INTERVAL 45 DAY, @now - INTERVAL 30 DAY, 3),
 ('70000000-0000-0000-0000-000000000105', '40000000-0000-0000-0000-000000000002',
  '10000000-0000-0000-0000-000000000204', '50000000-0000-0000-0000-000000000204',
  '60000000-0000-0000-0000-000000000105', 'takeshi@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 9 DAY, @now - INTERVAL 1 DAY, 2),
 ('70000000-0000-0000-0000-000000000106', '40000000-0000-0000-0000-000000000009',
  '10000000-0000-0000-0000-000000000205', '50000000-0000-0000-0000-000000000205',
  '60000000-0000-0000-0000-000000000106', 'priya@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 8 DAY, @now - INTERVAL 20 HOUR, 2),
 ('70000000-0000-0000-0000-000000000107', '40000000-0000-0000-0000-000000000008',
  '10000000-0000-0000-0000-000000000206', '50000000-0000-0000-0000-000000000206',
  '60000000-0000-0000-0000-000000000107', 'carlos@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 7 DAY, @now - INTERVAL 15 HOUR, 3),
 ('70000000-0000-0000-0000-000000000108', '40000000-0000-0000-0000-000000000011',
  '10000000-0000-0000-0000-000000000207', '50000000-0000-0000-0000-000000000207',
  '60000000-0000-0000-0000-000000000108', 'fatima@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 6 DAY, @now - INTERVAL 10 HOUR, 2),
 ('70000000-0000-0000-0000-000000000109', '40000000-0000-0000-0000-000000000010',
  '10000000-0000-0000-0000-000000000208', '50000000-0000-0000-0000-000000000208',
  '60000000-0000-0000-0000-000000000109', 'jonas@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 5 DAY, @now - INTERVAL 5 HOUR, 4),
 ('70000000-0000-0000-0000-000000000110', '40000000-0000-0000-0000-000000000005',
  '10000000-0000-0000-0000-000000000209', '50000000-0000-0000-0000-000000000209',
  '60000000-0000-0000-0000-000000000110', 'ananya@demo.local', TRUE,
  'INTERVIEW', @now - INTERVAL 4 DAY, @now - INTERVAL 4 HOUR, 3),
 ('70000000-0000-0000-0000-000000000111', '40000000-0000-0000-0000-000000000012',
  '10000000-0000-0000-0000-000000000210', '50000000-0000-0000-0000-000000000210',
  '60000000-0000-0000-0000-000000000111', 'grace@demo.local', TRUE,
  'IN_REVIEW', @now - INTERVAL 3 DAY, @now - INTERVAL 2 HOUR, 1);

-- Keep applicant_count correct no matter when this script runs.
UPDATE jobs j
SET applicant_count = (SELECT COUNT(*) FROM applications a WHERE a.job_id = j.id)
WHERE j.id IN ('40000000-0000-0000-0000-000000000001','40000000-0000-0000-0000-000000000002',
               '40000000-0000-0000-0000-000000000005','40000000-0000-0000-0000-000000000006',
               '40000000-0000-0000-0000-000000000007','40000000-0000-0000-0000-000000000008',
               '40000000-0000-0000-0000-000000000009','40000000-0000-0000-0000-000000000010',
               '40000000-0000-0000-0000-000000000011','40000000-0000-0000-0000-000000000012');
