package com.x.bpm.form.adapter.persistence;

import com.x.bpm.form.application.FormTransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringFormTransactionRunner implements FormTransactionRunner {
    private final TransactionTemplate transactions;

    public SpringFormTransactionRunner(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
