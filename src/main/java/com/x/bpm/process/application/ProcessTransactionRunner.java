package com.x.bpm.process.application;

import java.util.function.Supplier;

public interface ProcessTransactionRunner {
    <T> T required(Supplier<T> work);
}
