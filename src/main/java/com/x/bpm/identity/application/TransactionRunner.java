package com.x.bpm.identity.application;

import java.util.function.Supplier;

/** Cổng transaction do application sở hữu; implementation Spring nằm ở adapter. */
public interface TransactionRunner {
    <T> T required(Supplier<T> work);

    /** Chạy và commit độc lập với transaction bên ngoài, dùng cho security revocation. */
    <T> T requiresNew(Supplier<T> work);
}
