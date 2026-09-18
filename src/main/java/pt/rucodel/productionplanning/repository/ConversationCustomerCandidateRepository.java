package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.ConversationCustomerCandidateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationCustomerCandidateRepository extends JpaRepository<ConversationCustomerCandidateEntity, UUID> {
    List<ConversationCustomerCandidateEntity> findByConversationIdOrderByPositionAsc(UUID conversationId);

    Optional<ConversationCustomerCandidateEntity> findByConversationIdAndPosition(UUID conversationId, int position);

    void deleteByConversationId(UUID conversationId);
}
