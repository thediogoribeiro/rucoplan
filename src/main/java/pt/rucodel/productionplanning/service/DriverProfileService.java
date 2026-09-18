package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.rucodel.productionplanning.entity.DriverEntity;
import pt.rucodel.productionplanning.exception.EntityNotFoundException;
import pt.rucodel.productionplanning.repository.DriverRepository;

import java.util.UUID;

@Service
public class DriverProfileService {
    private final DriverRepository drivers;
    private final DriverRegistrationService registration;

    public DriverProfileService(DriverRepository drivers, DriverRegistrationService registration) {
        this.drivers = drivers;
        this.registration = registration;
    }

    @Transactional
    public DriverEntity updateName(UUID driverId, String rawName, String actor) {
        DriverEntity driver = drivers.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Driver was not found."));
        driver.setName(registration.validateAndNormalizeName(rawName));
        driver.setUpdatedBy(actor);
        return driver;
    }
}
