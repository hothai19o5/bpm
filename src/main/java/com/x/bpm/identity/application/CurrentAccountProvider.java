package com.x.bpm.identity.application;

/** Cổng application đọc principal đã xác thực, không phụ thuộc Spring Security. */
public interface CurrentAccountProvider {
    AuthenticatedAccount requireCurrentAccount();
}
