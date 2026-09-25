package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.rucodel.productionplanning.entity.DriverEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {
    Optional<DriverEntity> findByExternalId(String externalId);

    Optional<DriverEntity> findByDriverCode(String driverCode);

    Optional<DriverEntity> findByTelegramUserId(Long telegramUserId);

    @Query(value = "select nextval('driver_code_seq')", nativeQuery = true)
    Integer nextDriverCodeNumber();

    List<DriverEntity> findByActiveTrueOrderByName();
}
