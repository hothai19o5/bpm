package com.x.bpm.process.application;
import java.util.UUID;
/** Minimal public catalog: no XML, engine mapping, owner or step metadata. */
public record ProcessStartOption(UUID id,String key,String name,UUID versionId,int versionNumber) { }
