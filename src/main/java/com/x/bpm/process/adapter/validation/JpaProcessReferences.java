package com.x.bpm.process.adapter.validation;
import com.x.bpm.process.application.ProcessReferences;
import com.x.bpm.form.application.FormCatalogStore;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
@Transactional(readOnly=true)
public class JpaProcessReferences implements ProcessReferences {
    private final EntityManager entities;
    private final FormCatalogStore forms;
    public JpaProcessReferences(EntityManager entities, FormCatalogStore forms) {this.entities=entities;this.forms=forms;}
    public Optional<Map<String,Object>> publishedForm(UUID id) {return forms.bindingVersion(id).map(a -> a.version().schema());}
    public boolean active(String type, UUID id) {
        String entity=switch(type) {case "specificUserId" -> "UserEntity";case "departmentId" -> "DepartmentEntity";case "roleId" -> "RoleEntity";default -> throw new IllegalArgumentException("Unknown reference");};
        return entities.createQuery("select count(e) from " + entity + " e where e.id = :id and e.status = 'ACTIVE'",Long.class).setParameter("id",id).getSingleResult()==1;
    }
}
