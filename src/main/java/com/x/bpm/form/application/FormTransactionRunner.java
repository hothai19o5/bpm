package com.x.bpm.form.application;

import java.util.function.Supplier;

public interface FormTransactionRunner {
    <T> T required(Supplier<T> work);
}
