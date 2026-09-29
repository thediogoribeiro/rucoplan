package pt.rucodel.productionplanning.security;

import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.LoginRequest;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;

public interface AuthenticationProvider {
    AuthenticatedAccount authenticate(LoginRequest request);

    ProductionSiteEntity requireSite(AuthenticatedAccount account, ProductionSiteCode siteCode);
}
