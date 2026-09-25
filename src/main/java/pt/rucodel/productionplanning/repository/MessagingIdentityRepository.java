package pt.rucodel.productionplanning.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.MessagingChannel;
import pt.rucodel.productionplanning.entity.MessagingIdentityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessagingIdentityRepository extends JpaRepository<MessagingIdentityEntity, UUID> {
    Optional<MessagingIdentityEntity> findByChannelAndIntegrationKeyAndExternalUserId(
            MessagingChannel channel, String integrationKey, String externalUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select identity from MessagingIdentityEntity identity
            where identity.channel = :channel
              and identity.integrationKey = :integrationKey
              and identity.externalUserId = :externalUserId
            """)
    Optional<MessagingIdentityEntity> findWithLockByChannelAndIntegrationKeyAndExternalUserId(
            @Param("channel") MessagingChannel channel,
            @Param("integrationKey") String integrationKey,
            @Param("externalUserId") String externalUserId);

    List<MessagingIdentityEntity> findByDriverId(UUID driverId);

    @Query("""
            select identity from MessagingIdentityEntity identity
            left join fetch identity.driver
            order by identity.lastSeenAt desc
            """)
    List<MessagingIdentityEntity> findAllWithDriverOrderByLastSeenAtDesc();
}
