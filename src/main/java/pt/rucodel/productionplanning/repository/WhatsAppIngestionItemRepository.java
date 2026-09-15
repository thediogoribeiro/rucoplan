package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.WhatsAppIngestionItemEntity;

import java.util.Optional;
import java.util.UUID;

public interface WhatsAppIngestionItemRepository extends JpaRepository<WhatsAppIngestionItemEntity, UUID> {
    Optional<WhatsAppIngestionItemEntity> findByExternalMessageId(String externalMessageId);
}
