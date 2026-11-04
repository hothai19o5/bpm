package com.x.bpm.process.adapter.persistence;

import com.x.bpm.process.application.ProcessTransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringProcessTransactionRunner implements ProcessTransactionRunner {
    private final TransactionTemplate transactions;
    public SpringProcessTransactionRunner(TransactionTemplate transactions) { this.transactions = transactions; }
    @Override public <T> T required(Supplier<T> work) { return transactions.execute(status -> work.get()); }
}
