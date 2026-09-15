package pt.rucodel.productionplanning.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import pt.rucodel.productionplanning.entity.TelegramConversationEntity;

import java.util.Optional;
import java.util.UUID;

public interface TelegramConversationRepository extends JpaRepository<TelegramConversationEntity, UUID> {
    Optional<TelegramConversationEntity> findByTelegramUserId(Long telegramUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TelegramConversationEntity> findWithLockByTelegramUserId(Long telegramUserId);
}
