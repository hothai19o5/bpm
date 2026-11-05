package com.x.bpm.businessrecord.application;
import com.x.bpm.form.application.FormDataPatch;
public record SubmitRecord(Long expectedRevision,String title,FormDataPatch patch) { }
