package com.adproject.candidate.feature.profile

// Curated lists shown as multi-select chips on the job preferences screen. Candidates can still
// add a title/location that is not listed here via the "Add a …" field.
val COMMON_JOB_TITLES: List<String> = listOf(
    "Software Engineer", "Backend Engineer", "Frontend Engineer", "Full-stack Engineer",
    "Android Engineer", "iOS Engineer", "Mobile Developer", "DevOps Engineer", "Cloud Engineer",
    "Data Scientist", "Machine Learning Engineer", "Data Analyst", "Data Engineer", "QA Engineer",
    "Product Manager", "Project Manager", "UX Designer", "UI Designer", "Product Designer",
    "Business Analyst", "Marketing", "Sales", "Customer Service", "Recruiter", "HR",
    "Finance", "Accountant", "Intern",
)

val COMMON_LOCATIONS: List<String> = listOf(
    "Singapore", "Remote", "Central", "East", "West", "North", "North-East",
)

// Preset monthly salary figures (SGD) shown as single-select chips.
val SALARY_OPTIONS: List<Long> = listOf(1000, 2000, 2500, 3000, 4000, 5000, 6000, 8000, 10000, 15000, 20000)
