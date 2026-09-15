package pt.rucodel.productionplanning.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WheelIntakeRequestRepository extends JpaRepository<WheelIntakeRequestEntity, UUID> {
    Optional<WheelIntakeRequestEntity> findByExternalMessageId(String externalMessageId);

    Page<WheelIntakeRequestEntity> findByDriverId(UUID driverId, Pageable pageable);

    @Query("""
            select r from WheelIntakeRequestEntity r
            where (:driverId is null or r.driver.id = :driverId)
              and (:customerId is null or r.customer.id = :customerId)
              and (:status is null or r.lifecycleStatus = :status)
            order by r.requestedFactoryPickupWindowStart asc, r.createdAt asc
            """)
    Page<WheelIntakeRequestEntity> search(
            @Param("driverId") UUID driverId,
            @Param("customerId") UUID customerId,
            @Param("status") LifecycleStatus status,
            Pageable pageable
    );

    @Query("""
            select r from WheelIntakeRequestEntity r
            join fetch r.driver
            join fetch r.customer
            where r.lifecycleStatus not in :closedStatuses
            order by r.requestedFactoryPickupWindowStart asc, r.createdAt asc
            """)
    List<WheelIntakeRequestEntity> findOpenRequestsForPlanning(@Param("closedStatuses") Collection<LifecycleStatus> closedStatuses);

    @Query("""
            select r from WheelIntakeRequestEntity r
            where r.lifecycleStatus not in :closedStatuses
              and r.expectedFactoryDropOffWindowEnd < :before
              and r.actualFactoryArrivalAt is null
            order by r.expectedFactoryDropOffWindowEnd asc
            """)
    List<WheelIntakeRequestEntity> findDelayedExpectedArrivals(
            @Param("closedStatuses") Collection<LifecycleStatus> closedStatuses,
            @Param("before") OffsetDateTime before
    );

}
