package pt.rucodel.productionplanning.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pt.rucodel.productionplanning.entity.TelegramInboundUpdateEntity;

import java.util.Optional;

public interface TelegramInboundUpdateRepository extends JpaRepository<TelegramInboundUpdateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TelegramInboundUpdateEntity> findWithLockByUpdateId(Long updateId);
}
