package com.x.bpm.organization.application;

import java.util.function.Supplier;

public interface OrganizationTransactionRunner {
    <T> T required(Supplier<T> work);
}
