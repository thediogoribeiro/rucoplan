package pt.rucodel.productionplanning.security;

public interface TokenAuthenticationValidator {
    AuthenticatedUser validate(TokenClaims claims);
}
