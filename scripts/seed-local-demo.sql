-- Local demo data for the adproject MySQL schema.
-- Every demo account uses the password: password
-- This script is destructive: it clears all business data but preserves Flyway history.

SET FOREIGN_KEY_CHECKS = 0;
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
     version, created_at, updated_at)
VALUES
    ('50000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000101',
     'Alice Zhang', 25, 'Shanghai', 'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY),
    ('50000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000102',
     'Bob Lim', 27, 'Singapore', 'Android engineer · Kotlin · Jetpack Compose',
     'Android developer focused on Compose architecture, app performance, accessibility, and dependable API integration.',
     '[{"experienceId":"exp-bob-1","title":"Android Developer","company":"TransitGo","description":"Led a Compose migration and improved cold-start performance by 30 percent.","startDate":"2022-03","endDate":null}]',
     1, @now - INTERVAL 44 DAY, @now - INTERVAL 2 DAY);

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
     @now - INTERVAL 40 DAY, 3, @now - INTERVAL 42 DAY, @now - INTERVAL 2 DAY);

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
     'JOB_STATUS_CHANGED', 'ACTIVE', 'CLOSED', @now - INTERVAL 2 DAY, 'Hiring cycle completed', 'seed-job-4-close');

INSERT INTO resume_snapshots
    (id, resume_id, candidate_id, full_name, age, location, headline, summary, experiences_json,
     resume_version, resume_created_at, resume_updated_at, captured_at)
VALUES
    ('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000101',
     '10000000-0000-0000-0000-000000000101', 'Alice Zhang', 25, 'Shanghai',
     'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY, @now - INTERVAL 8 DAY),
    ('60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000102',
     '10000000-0000-0000-0000-000000000102', 'Bob Lim', 27, 'Singapore',
     'Android engineer · Kotlin · Jetpack Compose',
     'Android developer focused on Compose architecture, app performance, accessibility, and dependable API integration.',
     '[{"experienceId":"exp-bob-1","title":"Android Developer","company":"TransitGo","description":"Led a Compose migration and improved cold-start performance by 30 percent.","startDate":"2022-03","endDate":null}]',
     1, @now - INTERVAL 44 DAY, @now - INTERVAL 2 DAY, @now - INTERVAL 6 DAY),
    ('60000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000101',
     '10000000-0000-0000-0000-000000000101', 'Alice Zhang', 25, 'Shanghai',
     'Full-stack engineer · React · Spring Boot',
     'Product-minded engineer with three years of experience delivering web applications and reliable Java APIs.',
     '[{"experienceId":"exp-alice-1","title":"Software Engineer","company":"Harbor Labs","description":"Built React workflows and Spring Boot services used by regional operations teams.","startDate":"2023-07","endDate":null}]',
     2, @now - INTERVAL 58 DAY, @now - INTERVAL 1 DAY, @now - INTERVAL 25 DAY);

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
