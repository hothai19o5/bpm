package com.x.bpm.businessrecord.application;
import com.x.bpm.form.application.FormDataPatch;
public record SaveRecordDraft(Long expectedRevision,String title,FormDataPatch patch) { }
