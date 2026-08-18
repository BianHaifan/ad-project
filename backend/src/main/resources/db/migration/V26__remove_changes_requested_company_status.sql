UPDATE companies
SET verification_status = 'REJECTED'
WHERE verification_status = 'CHANGES_REQUESTED';
