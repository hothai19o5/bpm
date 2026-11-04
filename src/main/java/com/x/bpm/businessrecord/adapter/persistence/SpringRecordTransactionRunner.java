package com.x.bpm.businessrecord.adapter.persistence;
import com.x.bpm.businessrecord.application.RecordTransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class SpringRecordTransactionRunner implements RecordTransactionRunner {
    private final TransactionTemplate transactions;
    public SpringRecordTransactionRunner(PlatformTransactionManager manager){transactions=new TransactionTemplate(manager);}
    public <T>T required(Supplier<T> work){return transactions.execute(status->work.get());}
}
