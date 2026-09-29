package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.DriverProductionSiteAssociationSource;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.dto.ProductionSiteResponse;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.entity.ApplicationUserSiteEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.entity.DriverProductionSiteEntity;
import pt.rucodel.productionplanning.entity.ProductionSiteEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.ForbiddenOperationException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.repository.ApplicationUserSiteRepository;
import pt.rucodel.productionplanning.repository.DriverProductionSiteRepository;
import pt.rucodel.productionplanning.repository.ProductionSiteRepository;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ProductionSiteService {
    private final ProductionSiteRepository sites;
    private final ApplicationUserSiteRepository userSites;
    private final DriverProductionSiteRepository driverSites;

    public ProductionSiteService(ProductionSiteRepository sites,
                                 ApplicationUserSiteRepository userSites,
                                 DriverProductionSiteRepository driverSites) {
        this.sites = sites;
        this.userSites = userSites;
        this.driverSites = driverSites;
    }

    @Transactional(readOnly = true)
    public ProductionSiteEntity requireActive(ProductionSiteCode code) {
        ProductionSiteEntity site = sites.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Production site was not found."));
        if (!site.isActive()) {
            throw new InvalidRequestException("PRODUCTION_SITE_INACTIVE", "A unidade de produção não está ativa.");
        }
        return site;
    }

    @Transactional(readOnly = true)
    public ProductionSiteEntity requireUserSite(ApplicationUserEntity user, ProductionSiteCode code) {
        ProductionSiteEntity site = requireActive(code);
        userSites.findByUserIdAndProductionSiteCodeAndActiveTrue(user.getId(), code)
                .orElseThrow(() -> new ForbiddenOperationException("Não tem acesso à unidade de produção selecionada."));
        return site;
    }

    @Transactional(readOnly = true)
    public ProductionSiteEntity requireById(UUID id) {
        return sites.findById(id).orElseThrow(() -> new EntityNotFoundException("Production site was not found."));
    }

    @Transactional(readOnly = true)
    public ProductionSiteEntity requireByCode(ProductionSiteCode code) {
        return sites.findByCode(code).orElseThrow(() -> new EntityNotFoundException("Production site was not found."));
    }

    @Transactional(readOnly = true)
    public ProductionSiteEntity portugal() {
        return requireByCode(ProductionSiteCode.PT);
    }

    @Transactional(readOnly = true)
    public List<ProductionSiteEntity> activeSites() {
        return sites.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional
    public void ensureDriverAssociation(DriverEntity driver, ProductionSiteEntity site,
                                        DriverProductionSiteAssociationSource source, String actor) {
        DriverProductionSiteEntity existing = driverSites
                .findByDriverIdAndProductionSiteCode(driver.getId(), site.getCode())
                .orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                existing.setActive(true);
                existing.setAssociationSource(source);
                existing.setAssociatedBy(actor);
                driverSites.save(existing);
            }
            return;
        }
        DriverProductionSiteEntity association = new DriverProductionSiteEntity();
        association.setDriver(driver);
        association.setProductionSite(site);
        association.setAssociationSource(source);
        association.setAssociatedBy(actor);
        association.setActive(true);
        driverSites.save(association);
    }

    @Transactional
    public void ensureUserAssociation(ApplicationUserEntity user, ProductionSiteEntity site, String actor) {
        ApplicationUserSiteEntity existing = userSites
                .findByUserIdAndProductionSiteCodeAndActiveTrue(user.getId(), site.getCode())
                .orElse(null);
        if (existing != null) {
            return;
        }
        ApplicationUserSiteEntity association = new ApplicationUserSiteEntity();
        association.setUser(user);
        association.setProductionSite(site);
        association.setRole(user.getRole());
        association.setActive(true);
        association.setCreatedBy(actor == null || actor.isBlank() ? "SYSTEM" : actor);
        userSites.save(association);
    }

    @Transactional(readOnly = true)
    public boolean driverHasAccess(DriverEntity driver, ProductionSiteEntity site) {
        return driver != null && driverSites.existsByDriverIdAndProductionSiteCodeAndActiveTrue(driver.getId(), site.getCode());
    }

    public ZoneId zone(ProductionSiteEntity site) {
        return ZoneId.of(site.getTimezone());
    }

    public ProductionSiteResponse toResponse(ProductionSiteEntity site) {
        return new ProductionSiteResponse(site.getId(), site.getCode(), site.getDisplayName(), site.getTimezone(), site.isActive());
    }
}
