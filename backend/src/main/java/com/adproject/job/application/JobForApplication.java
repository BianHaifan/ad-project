package com.adproject.job.application;

import com.adproject.company.application.CompanyView;

public record JobForApplication(String jobId, String title, CompanyView company) {}
