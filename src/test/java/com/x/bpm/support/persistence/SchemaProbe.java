package com.x.bpm.support.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Technical fixture only; absent from the application JAR and dev schema. */
@Entity
@Table(name = "p14_schema_probe")
public class SchemaProbe {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String label;

    protected SchemaProbe() {
    }

    public String getLabel() {
        return label;
    }
}
