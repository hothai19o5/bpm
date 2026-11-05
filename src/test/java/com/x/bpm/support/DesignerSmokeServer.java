package com.x.bpm.support;

import com.x.bpm.BpmApplication;
import com.x.bpm.identity.application.*;
import com.x.bpm.identity.domain.AccountStatus;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in real-browser fixture. Test classpath only; never packaged into the application. */
public final class DesignerSmokeServer {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("BPM_DESIGNER_SMOKE"))) throw new IllegalStateException("Explicit smoke opt-in required");
        Path output=Path.of("target/designer-smoke");Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("ready"));Files.deleteIfExists(output.resolve("stop"));
        var app=new SpringApplication(BpmApplication.class,PostgresTestConfiguration.class);
        app.setAdditionalProfiles("test");
        try(var context=app.run(args)) {
            var accounts=context.getBean(AuthAccountPort.class);var passwords=context.getBean(PasswordHashPort.class);
            var user=accounts.create(new NewUserAccount("designer.smoke","Designer smoke fixture",
                    passwords.hash("designer-smoke-only-2026"),AccountStatus.ACTIVE,false));
            var jdbc=context.getBean(JdbcTemplate.class);UUID role=UUID.randomUUID();
            jdbc.update("insert into roles(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,'designer-smoke','Smoke only','ACTIVE')",role);
            jdbc.update("insert into user_roles(id,created_at,updated_at,revision,user_id,role_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),user.id(),role);
            for(UUID permission:jdbc.queryForList("select id from permissions",UUID.class))
                jdbc.update("insert into role_permissions(id,created_at,updated_at,revision,role_id,permission_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),role,permission);
            UUID department=UUID.randomUUID();
            jdbc.update("insert into departments(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,'designer-smoke','Smoke department','ACTIVE')",department);
            jdbc.update("insert into department_members(id,created_at,updated_at,revision,user_id,department_id,is_primary) values(?,current_timestamp,current_timestamp,0,?,?,true)",UUID.randomUUID(),user.id(),department);
            var reviewer=accounts.create(new NewUserAccount("reviewer.smoke","Reviewer smoke fixture",
                    passwords.hash("reviewer-smoke-only-2026"),AccountStatus.ACTIVE,false));
            UUID reviewerRole=UUID.randomUUID();
            jdbc.update("insert into roles(id,created_at,updated_at,revision,code,name,status) values(?,current_timestamp,current_timestamp,0,'reviewer-smoke','Reviewer smoke only','ACTIVE')",reviewerRole);
            jdbc.update("insert into user_roles(id,created_at,updated_at,revision,user_id,role_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),reviewer.id(),reviewerRole);
            for(String permission:java.util.List.of("task.claim","task.complete","record.read.participated"))
                jdbc.update("insert into role_permissions(id,created_at,updated_at,revision,role_id,permission_id) values(?,current_timestamp,current_timestamp,0,?,?)",UUID.randomUUID(),reviewerRole,jdbc.queryForObject("select id from permissions where code=?",UUID.class,permission));
            jdbc.update("insert into department_members(id,created_at,updated_at,revision,user_id,department_id,is_primary) values(?,current_timestamp,current_timestamp,0,?,?,true)",UUID.randomUUID(),reviewer.id(),department);
            Files.writeString(output.resolve("ready"),user.id().toString());
            Instant deadline=Instant.now().plus(Duration.ofMinutes(15));
            while(!Files.exists(output.resolve("stop"))&&Instant.now().isBefore(deadline))Thread.sleep(500);
            if(!Files.exists(output.resolve("stop")))throw new IllegalStateException("Browser smoke timed out");
        }
    }
}
