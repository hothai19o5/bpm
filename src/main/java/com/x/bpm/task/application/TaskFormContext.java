package com.x.bpm.task.application;

import com.x.bpm.form.domain.*;
import java.util.Map;
import java.util.List;

public record TaskFormContext(FormVersion form, FormFieldPolicy policy, Map<String,Object> data, List<FormAction> actions) { }
