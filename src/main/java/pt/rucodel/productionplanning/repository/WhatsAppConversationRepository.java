package pt.rucodel.productionplanning.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.entity.WhatsAppConversationEntity;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppConversationRepository extends JpaRepository<WhatsAppConversationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select conversation from WhatsAppConversationEntity conversation
            where conversation.messagingIdentity.id = :identityId
            """)
    Optional<WhatsAppConversationEntity> findWithLockByMessagingIdentityId(@Param("identityId") UUID identityId);
}
