package com.x.bpm.organization.adapter.persistence;

import com.x.bpm.organization.application.OrganizationTransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringOrganizationTransactionRunner implements OrganizationTransactionRunner {
    private final TransactionTemplate transactions;

    public SpringOrganizationTransactionRunner(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
