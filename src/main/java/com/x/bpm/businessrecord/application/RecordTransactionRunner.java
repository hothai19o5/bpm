package com.x.bpm.businessrecord.application;
import java.util.function.Supplier;
public interface RecordTransactionRunner {<T>T required(Supplier<T> work);}
