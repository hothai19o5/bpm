package com.x.bpm.task.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.businessrecord.application.RecordTransactionRunner;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.form.domain.*;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.application.*;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.application.engine.TaskEnginePort;
import com.x.bpm.task.assignment.application.*;
import com.x.bpm.task.assignment.domain.AssignmentStrategy;
import java.util.*;

public final class TaskInboxUseCase {
    public enum Box { ASSIGNED, CANDIDATE }
    private final TaskRuntimeStore store;
    private final RecordTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final AssignmentDirectoryPort directory;
    private final AssignmentResolver resolver;
    private final TaskEnginePort engine;
    private final FormDataService forms;
    private final AuditEventRecorder audit;

    public TaskInboxUseCase(TaskRuntimeStore store, RecordTransactionRunner transactions, CurrentActorProvider actors,
            AssignmentDirectoryPort directory, AssignmentResolver resolver, TaskEnginePort engine, FormDataService forms, AuditEventRecorder audit) {
        this.store=store;this.transactions=transactions;this.actors=actors;this.directory=directory;
        this.resolver=resolver;this.engine=engine;this.forms=forms;this.audit=audit;
    }

    public PageResult<TaskSummary> list(Box box, String q, PageQuery page) {
        var actor=actors.requireCurrentActor(); require(actor,"task.complete");
        if(box==null)throw invalid(); if(box==Box.CANDIDATE)require(actor,"task.claim");
        String search=q==null?"":q.strip().toLowerCase(Locale.ROOT);
        if(search.length()>200||!page.sort().equals("createdAt,desc")||page.offset()>Integer.MAX_VALUE)throw invalid();
        return transactions.required(()->{
            var source=box==Box.ASSIGNED?store.assigned(actor.userId()):store.candidates(groups(actor));
            var visible=source.stream().filter(task->box==Box.ASSIGNED?assigned(actor,task):candidate(actor,task))
                    .filter(task->box!=Box.CANDIDATE||task.engine().assignee()==null)
                    .filter(task->(task.recordTitle()+" "+task.businessKey()+" "+Objects.toString(task.engine().name(),"")).toLowerCase(Locale.ROOT).contains(search))
                    .sorted(Comparator.comparing((BusinessTask task)->task.engine().createdAt(),Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(task->task.engine().taskId())).toList();
            return new PageResult<>(visible.stream().skip(page.offset()).limit(page.size()).map(TaskInboxUseCase::summary).toList(),page.page(),page.size(),visible.size());
        });
    }

    public TaskDetail detail(String taskId) {
        var actor=actors.requireCurrentActor();require(actor,"task.complete");
        return transactions.required(()->{var task=store.find(taskId);if(!assigned(actor,task)&&!(task.engine().assignee()==null&&candidate(actor,task)))throw forbidden();return view(actor,task);});
    }

    public TaskDetail claim(String taskId) {
        var actor=actors.requireCurrentActor();require(actor,"task.claim");require(actor,"task.complete");
        return transactions.required(()->{
            var found=store.find(taskId);store.lockRecord(found.recordId());var task=store.find(taskId);
            // Lost response retry by the same current assignee has no second mutation or audit.
            if(assigned(actor,task))return view(actor,task);
            if(!candidate(actor,task))throw forbidden();
            if(task.engine().assignee()!=null)throw new ApplicationException(ErrorCode.TASK_CONFLICT,"Công việc đã được người khác nhận. Tải lại danh sách.");
            engine.claim(taskId,actor.userId(),groups(actor));
            audit.record("TASK_CLAIMED","ENGINE_TASK",taskId,Map.of("recordId",task.recordId().toString(),"assigneeId",actor.userId()));
            return view(actor,store.find(taskId));
        });
    }

    public boolean assigned(ActorContext actor,BusinessTask task) {
        return actor.hasPermission("task.complete")&&actor.userId().equals(task.engine().assignee())
                &&(task.requesterCorrection()||!actor.userId().equals(task.requesterId().toString()));
    }

    public boolean candidate(ActorContext actor,BusinessTask task) {
        var request=task.assignment();
        if(!actor.hasPermission("task.claim")||!actor.hasPermission("task.complete")||task.requesterCorrection()
                ||actor.userId().equals(task.requesterId().toString())||request==null
                ||(request.strategy()!=AssignmentStrategy.DEPARTMENT&&request.strategy()!=AssignmentStrategy.ROLE)
                ||!task.engine().candidateGroupIds().contains(RuntimeAssignmentUseCase.candidateGroup(request)))return false;
        return resolver.resolve(request).candidateUserIds().contains(UUID.fromString(actor.userId()));
    }

    private Set<String> groups(ActorContext actor) {
        var user=directory.findUser(UUID.fromString(actor.userId())).filter(AssignmentUser::active).orElse(null);
        if(user==null||user.departmentId()==null||!user.hasPermission("task.claim")||!user.hasPermission("task.complete")
                ||directory.findDepartment(user.departmentId()).filter(AssignmentDepartment::active).isEmpty())return Set.of();
        var groups=new LinkedHashSet<String>();String prefix="bpm:department:"+user.departmentId();groups.add(prefix);
        user.roleIds().forEach(role->{if(directory.findRole(role).filter(AssignmentRole::active).isPresent())groups.add(prefix+":role:"+role);});
        return Set.copyOf(groups);
    }

    private TaskDetail view(ActorContext actor,BusinessTask task) {
        var context=store.form(task);var readOnly=new LinkedHashMap<String,FormFieldAccess>();
        context.policy().access().forEach((id,access)->readOnly.put(id,access==FormFieldAccess.HIDDEN?access:FormFieldAccess.READ_ONLY));
        boolean canComplete=assigned(actor,task);
        return new TaskDetail(summary(task),task.recordStatus(),task.recordRevision(),forms.read(context.form(),context.data(),canComplete?context.policy():new FormFieldPolicy(readOnly)),
                task.engine().assignee()==null&&candidate(actor,task),canComplete?context.actions():List.of());
    }
    public static TaskSummary summary(BusinessTask task){var engine=task.engine();return new TaskSummary(engine.taskId(),task.recordId(),task.businessKey(),task.recordTitle(),engine.name(),engine.taskDefinitionKey(),engine.assignee(),engine.createdAt());}
    private static void require(ActorContext actor,String permission){if(!actor.hasPermission(permission))throw forbidden();}
    private static ApplicationException forbidden(){return new ApplicationException(ErrorCode.FORBIDDEN,"Bạn không có quyền xử lý công việc này.");}
    private static ApplicationException invalid(){return new ApplicationException(ErrorCode.INVALID_REQUEST,"Bộ lọc công việc không hợp lệ.");}
}
