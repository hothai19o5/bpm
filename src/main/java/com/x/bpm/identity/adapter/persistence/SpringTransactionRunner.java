package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.TransactionRunner;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Adapter transaction để application không phụ thuộc annotation/framework. */
@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactions;

    public SpringTransactionRunner(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T required(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        return transactions.execute(status -> work.get());
    }
}
