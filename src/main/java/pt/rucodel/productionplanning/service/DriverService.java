package pt.rucodel.productionplanning.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.domain.UserRole;
import pt.rucodel.productionplanning.dto.DriverRequest;
import pt.rucodel.productionplanning.dto.DriverResponse;
import pt.rucodel.productionplanning.dto.AdminDriverDetailResponse;
import pt.rucodel.productionplanning.entity.ApplicationUserEntity;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.exception.InvalidRequestException;
import pt.rucodel.productionplanning.mapper.ApiMapper;
import pt.rucodel.productionplanning.repository.ApplicationUserRepository;
import pt.rucodel.productionplanning.repository.DriverRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DriverService {
    private final DriverRepository drivers;
    private final ApplicationUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApiMapper mapper;
    private final RecalculationService recalculationService;
    private final AdminMessagingIdentityService adminMessagingIdentities;
    private final PublicCodeService publicCodes;

    public DriverService(DriverRepository drivers, ApplicationUserRepository users, PasswordEncoder passwordEncoder,
                         ApiMapper mapper, RecalculationService recalculationService,
                         AdminMessagingIdentityService adminMessagingIdentities,
                         PublicCodeService publicCodes) {
        this.drivers = drivers;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.recalculationService = recalculationService;
        this.adminMessagingIdentities = adminMessagingIdentities;
        this.publicCodes = publicCodes;
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> list() {
        return drivers.findAll().stream().map(mapper::toDriver).toList();
    }

    @Transactional(readOnly = true)
    public AdminDriverDetailResponse get(UUID id) {
        DriverEntity driver = drivers.findById(id).orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        return adminMessagingIdentities.driverDetail(driver);
    }

    @Transactional
    public DriverResponse create(DriverRequest request, String actor) {
        DriverEntity driver = new DriverEntity();
        apply(driver, request);
        driver.setDriverCode(publicCodes.driverCode(drivers.nextDriverCodeNumber()));
        driver.setCreatedBy(actor);
        driver.setUpdatedBy(actor);
        DriverEntity saved = drivers.save(driver);
        if (request.username() != null && !request.username().isBlank()) {
            if (request.password() == null || request.password().isBlank()) {
                throw new InvalidRequestException("Driver account password is required.");
            }
            createDriverUser(saved, request.username(), request.password(), actor);
        }
        recalculationService.markCurrentAndFuturePlans();
        return mapper.toDriver(saved);
    }

    @Transactional
    public DriverResponse update(UUID id, DriverRequest request, String actor) {
        DriverEntity driver = drivers.findById(id).orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        if (request.version() != null && request.version() != driver.getVersion()) {
            throw new InvalidRequestException("OPTIMISTIC_LOCK", "Driver was changed by another user.");
        }
        apply(driver, request);
        driver.setUpdatedBy(actor);
        recalculationService.markCurrentAndFuturePlans();
        return mapper.toDriver(driver);
    }

    private void apply(DriverEntity driver, DriverRequest request) {
        if (request.externalId() != null) {
            driver.setExternalId(blankToNull(request.externalId()));
        }
        if (request.rucofiId() != null) {
            driver.setRucofiId(blankToNull(request.rucofiId()));
        }
        driver.setName(request.name().trim());
        driver.setActive(request.active() == null || request.active());
    }

    private void createDriverUser(DriverEntity driver, String username, String password, String actor) {
        if (users.existsByUsername(username.trim())) {
            throw new InvalidRequestException("DUPLICATE_USER", "A user with that username already exists.");
        }
        ApplicationUserEntity user = new ApplicationUserEntity();
        user.setUsername(username.trim());
        user.setDisplayName(driver.getName());
        user.setRole(UserRole.DRIVER);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDriver(driver);
        user.setActive(true);
        user.setCreatedBy(actor);
        user.setUpdatedBy(actor);
        users.save(user);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
