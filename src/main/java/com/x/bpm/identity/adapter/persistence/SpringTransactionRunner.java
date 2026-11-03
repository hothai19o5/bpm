package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.TransactionRunner;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Adapter transaction để application không phụ thuộc annotation/framework. */
@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate requiredTransactions;
    private final TransactionTemplate newTransactions;

    public SpringTransactionRunner(TransactionTemplate transactions) {
        this.requiredTransactions = Objects.requireNonNull(transactions, "transactions");
        this.newTransactions = new TransactionTemplate(Objects.requireNonNull(
                transactions.getTransactionManager(), "transactionManager"));
        this.newTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        return requiredTransactions.execute(status -> work.get());
    }

    @Override
    public <T> T requiresNew(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        return newTransactions.execute(status -> work.get());
    }
}
