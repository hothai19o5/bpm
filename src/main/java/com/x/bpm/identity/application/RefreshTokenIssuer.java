package com.x.bpm.identity.application;

/** Tạo refresh token entropy cao và SHA-256 hash tương ứng. */
public interface RefreshTokenIssuer {
    IssuedRefreshToken issue();
}
