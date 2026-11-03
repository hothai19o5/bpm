package com.x.bpm.authorization.domain;

import java.util.Set;

/** Permission code ổn định; role chỉ gom các code này, không tham gia policy trực tiếp. */
public final class PermissionCatalog {
    public static final String IDENTITY_USERS_MANAGE = "identity.users.manage";
    public static final String IDENTITY_ROLES_MANAGE = "identity.roles.manage";
    public static final String ORGANIZATION_MANAGE = "organization.manage";
    public static final String PROCESS_CATALOG_READ = "process.catalog.read";
    public static final String PROCESS_DESIGN = "process.design";
    public static final String PROCESS_PUBLISH = "process.publish";
    public static final String PROCESS_START = "process.start";
    public static final String PROCESS_MONITOR_ALL = "process.monitor.all";
    public static final String PROCESS_MONITOR_OWNED = "process.monitor.owned";
    public static final String FORM_DESIGN = "form.design";
    public static final String FORM_PUBLISH = "form.publish";
    public static final String RECORD_CREATE = "record.create";
    public static final String RECORD_READ_ALL = "record.read.all";
    public static final String RECORD_READ_OWN = "record.read.own";
    public static final String RECORD_READ_PARTICIPATED = "record.read.participated";
    public static final String RECORD_UPDATE_DRAFT = "record.update.draft";
    public static final String RECORD_DELETE_DRAFT = "record.delete.draft";
    public static final String TASK_CLAIM = "task.claim";
    public static final String TASK_COMPLETE = "task.complete";
    public static final String TASK_REASSIGN = "task.reassign";
    public static final String AUDIT_READ = "audit.read";

    public static final Set<String> ALL = Set.of(IDENTITY_USERS_MANAGE,
            IDENTITY_ROLES_MANAGE, ORGANIZATION_MANAGE, PROCESS_CATALOG_READ,
            PROCESS_DESIGN, PROCESS_PUBLISH, PROCESS_START, PROCESS_MONITOR_ALL,
            PROCESS_MONITOR_OWNED, FORM_DESIGN, FORM_PUBLISH, RECORD_CREATE,
            RECORD_READ_ALL, RECORD_READ_OWN, RECORD_READ_PARTICIPATED,
            RECORD_UPDATE_DRAFT, RECORD_DELETE_DRAFT, TASK_CLAIM, TASK_COMPLETE,
            TASK_REASSIGN, AUDIT_READ);

    private PermissionCatalog() {
    }
}
