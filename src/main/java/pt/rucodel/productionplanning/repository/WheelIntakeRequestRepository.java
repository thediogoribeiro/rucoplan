package pt.rucodel.productionplanning.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.LifecycleStatus;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.WheelIntakeRequestEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WheelIntakeRequestRepository extends JpaRepository<WheelIntakeRequestEntity, UUID> {
    Optional<WheelIntakeRequestEntity> findByExternalMessageId(String externalMessageId);

    @Query("""
            select r from WheelIntakeRequestEntity r
            left join fetch r.driver
            left join fetch r.customer
            where r.productionSite.code = :siteCode
              and r.id = :id
            """)
    Optional<WheelIntakeRequestEntity> findByIdForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                       @Param("id") UUID id);

    boolean existsByRequestCode(String requestCode);

    Page<WheelIntakeRequestEntity> findByDriverId(UUID driverId, Pageable pageable);

    long countByDriverId(UUID driverId);

    long countByLifecycleStatus(LifecycleStatus lifecycleStatus);

    @Query("""
            select count(r) from WheelIntakeRequestEntity r
            where r.productionSite.code = :siteCode
              and r.lifecycleStatus = :lifecycleStatus
            """)
    long countByLifecycleStatusForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                       @Param("lifecycleStatus") LifecycleStatus lifecycleStatus);

    List<WheelIntakeRequestEntity> findByCustomerRegistrationRequestId(UUID customerRegistrationRequestId);

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
            where r.productionSite.code = :siteCode
              and (:driverId is null or r.driver.id = :driverId)
              and (:customerId is null or r.customer.id = :customerId)
              and (:status is null or r.lifecycleStatus = :status)
            order by r.requestedFactoryPickupWindowStart asc, r.createdAt asc
            """)
    Page<WheelIntakeRequestEntity> searchForSite(
            @Param("siteCode") ProductionSiteCode siteCode,
            @Param("driverId") UUID driverId,
            @Param("customerId") UUID customerId,
            @Param("status") LifecycleStatus status,
            Pageable pageable
    );

    @Query("""
            select r from WheelIntakeRequestEntity r
            left join fetch r.driver
            left join fetch r.customer
            where r.lifecycleStatus not in :closedStatuses
            order by r.requestedFactoryPickupWindowStart asc, r.createdAt asc
            """)
    List<WheelIntakeRequestEntity> findOpenRequestsForPlanning(@Param("closedStatuses") Collection<LifecycleStatus> closedStatuses);

    @Query("""
            select r from WheelIntakeRequestEntity r
            left join fetch r.driver
            left join fetch r.customer
            where r.productionSite.code = :siteCode
              and r.lifecycleStatus not in :closedStatuses
            order by r.requestedFactoryPickupWindowStart asc, r.createdAt asc
            """)
    List<WheelIntakeRequestEntity> findOpenRequestsForPlanningForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                       @Param("closedStatuses") Collection<LifecycleStatus> closedStatuses);

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

    @Query("""
            select r from WheelIntakeRequestEntity r
            where r.productionSite.code = :siteCode
              and r.lifecycleStatus not in :closedStatuses
              and r.expectedFactoryDropOffWindowEnd < :before
              and r.actualFactoryArrivalAt is null
            order by r.expectedFactoryDropOffWindowEnd asc
            """)
    List<WheelIntakeRequestEntity> findDelayedExpectedArrivalsForSite(
            @Param("siteCode") ProductionSiteCode siteCode,
            @Param("closedStatuses") Collection<LifecycleStatus> closedStatuses,
            @Param("before") OffsetDateTime before
    );

    @Query("""
            select r from WheelIntakeRequestEntity r
            left join fetch r.driver
            left join fetch r.customer
            where r.lifecycleStatus = :status
            order by
              case when r.expectedFactoryDropOffWindowEnd < CURRENT_TIMESTAMP then 0 else 1 end asc,
              r.expectedFactoryDropOffWindowStart asc,
              r.requestedFactoryPickupWindowStart asc,
              r.requestCode asc
            """)
    List<WheelIntakeRequestEntity> findFactoryArrivalQueue(@Param("status") LifecycleStatus status);

    @Query("""
            select r from WheelIntakeRequestEntity r
            left join fetch r.driver
            left join fetch r.customer
            where r.productionSite.code = :siteCode
              and r.lifecycleStatus = :status
            order by
              case when r.expectedFactoryDropOffWindowEnd < CURRENT_TIMESTAMP then 0 else 1 end asc,
              r.expectedFactoryDropOffWindowStart asc,
              r.requestedFactoryPickupWindowStart asc,
              r.requestCode asc
            """)
    List<WheelIntakeRequestEntity> findFactoryArrivalQueueForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                   @Param("status") LifecycleStatus status);

}
