package com.x.bpm.form.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FormDefinitionJpaRepository extends JpaRepository<FormDefinitionEntity, UUID> { }
