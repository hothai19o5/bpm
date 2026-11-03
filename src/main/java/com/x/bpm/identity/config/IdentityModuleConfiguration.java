package com.x.bpm.identity.config;

import com.x.bpm.identity.application.AccessTokenIssuer;
import com.x.bpm.identity.application.AccountSecurityUseCase;
import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.AuthGrantPort;
import com.x.bpm.identity.application.ChangePasswordUseCase;
import com.x.bpm.identity.application.CurrentAccountProvider;
import com.x.bpm.identity.application.LoginUseCase;
import com.x.bpm.identity.application.LogoutUseCase;
import com.x.bpm.identity.application.MeUseCase;
import com.x.bpm.identity.application.ManagedRolePort;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.application.RefreshTokenUseCase;
import com.x.bpm.identity.application.RefreshTokenIssuer;
import com.x.bpm.identity.application.ResolveAuthenticatedActorUseCase;
import com.x.bpm.identity.application.TransactionRunner;
import com.x.bpm.identity.application.UserAdministrationUseCase;
import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.application.OrganizationPort;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lắp ghép chính sách identity; adapter vẫn phụ thuộc vào các port phía application. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class IdentityModuleConfiguration {
    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }

    @Bean
    public LoginUseCase loginUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            PasswordHashPort passwordHashes, RefreshTokenIssuer refreshTokens,
            AccessTokenIssuer accessTokens, TransactionRunner transactions, Clock clock,
            AuthenticationProperties properties) {
        var dummyPasswordHash = passwordHashes.hash(
                "dummy-authentication-value-that-is-not-a-user-password");
        return new LoginUseCase(accounts, grants, passwordHashes, refreshTokens, accessTokens,
                transactions, clock, properties.refreshTokenTtl(), dummyPasswordHash);
    }

    @Bean
    public ResolveAuthenticatedActorUseCase resolveAuthenticatedActorUseCase(
            AuthAccountPort accounts, AuthGrantPort grants, TransactionRunner transactions,
            Clock clock) {
        return new ResolveAuthenticatedActorUseCase(accounts, grants, transactions, clock);
    }

    @Bean
    public RefreshTokenUseCase refreshTokenUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            RefreshTokenIssuer refreshTokens, AccessTokenIssuer accessTokens,
            TransactionRunner transactions, Clock clock) {
        return new RefreshTokenUseCase(accounts, grants, refreshTokens, accessTokens,
                transactions, clock);
    }

    @Bean
    public LogoutUseCase logoutUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            RefreshTokenIssuer refreshTokens, TransactionRunner transactions, Clock clock) {
        return new LogoutUseCase(accounts, grants, refreshTokens, transactions, clock);
    }

    @Bean
    public ChangePasswordUseCase changePasswordUseCase(CurrentAccountProvider currentAccounts,
            AuthAccountPort accounts, AuthGrantPort grants, PasswordHashPort passwordHashes,
            PasswordPolicy passwordPolicy, TransactionRunner transactions, Clock clock) {
        return new ChangePasswordUseCase(currentAccounts, accounts, grants, passwordHashes,
                passwordPolicy, transactions, clock);
    }

    @Bean
    public AccountSecurityUseCase accountSecurityUseCase(AuthAccountPort accounts,
            AuthGrantPort grants, PasswordHashPort passwordHashes, PasswordPolicy passwordPolicy,
            TransactionRunner transactions, Clock clock) {
        return new AccountSecurityUseCase(accounts, grants, passwordHashes, passwordPolicy,
                transactions, clock);
    }

    @Bean
    public UserAdministrationUseCase userAdministrationUseCase(AuthAccountPort accounts,
            ManagedRolePort roles, OrganizationPort organizationPort,
            OrganizationManagementUseCase organizations, PasswordHashPort passwordHashes,
            PasswordPolicy passwordPolicy, AccountSecurityUseCase accountSecurity,
            TransactionRunner transactions) {
        return new UserAdministrationUseCase(accounts, roles, organizationPort, organizations,
                passwordHashes, passwordPolicy, accountSecurity, transactions);
    }

    @Bean
    public MeUseCase meUseCase(CurrentAccountProvider currentAccounts) {
        return new MeUseCase(currentAccounts);
    }
}
