package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.ResolveAuthenticatedActorUseCase;
import com.x.bpm.shared.error.ApplicationException;
import java.util.Comparator;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/** Thay snapshot permissions trong JWT bằng account/grant/quyền hiện tại từ server. */
@Component
public class CurrentAccountJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {
    private final ResolveAuthenticatedActorUseCase resolver;

    public CurrentAccountJwtAuthenticationConverter(ResolveAuthenticatedActorUseCase resolver) {
        this.resolver = resolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var rawVersion = jwt.getClaim("ver");
            if (!(rawVersion instanceof Number version)) {
                throw new IllegalArgumentException("Missing token security version");
            }
            var account = resolver.resolve(jwt.getSubject(), jwt.getClaimAsString("sid"),
                    version.longValue());
            var authorities = account.permissions().stream().sorted(Comparator.naturalOrder())
                    .map(SimpleGrantedAuthority::new).toList();
            return new CurrentAccountAuthenticationToken(
                    new AuthenticatedAccountPrincipal(account), authorities);
        } catch (ApplicationException | IllegalArgumentException exception) {
            throw new InvalidBearerTokenException("Bearer identity is no longer valid", exception);
        }
    }
}
