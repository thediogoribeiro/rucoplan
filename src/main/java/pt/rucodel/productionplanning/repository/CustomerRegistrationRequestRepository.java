package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.domain.CustomerRegistrationStatus;
import pt.rucodel.productionplanning.entity.CustomerRegistrationRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRegistrationRequestRepository extends JpaRepository<CustomerRegistrationRequestEntity, UUID> {
    List<CustomerRegistrationRequestEntity> findByStatusOrderByCreatedAtAsc(CustomerRegistrationStatus status);

    List<CustomerRegistrationRequestEntity> findByNormalizedNameAndStatus(String normalizedName, CustomerRegistrationStatus status);

    Optional<CustomerRegistrationRequestEntity> findFirstByConversationIdAndStatusOrderByCreatedAtDesc(UUID conversationId, CustomerRegistrationStatus status);
}
