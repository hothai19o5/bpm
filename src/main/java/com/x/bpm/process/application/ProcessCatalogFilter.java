package com.x.bpm.process.application;
import com.x.bpm.shared.application.PageQuery;
public record ProcessCatalogFilter(String q,ProcessVersionSummary.Status versionStatus,PageQuery page) { }
