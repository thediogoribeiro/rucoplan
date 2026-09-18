package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.rucodel.productionplanning.entity.MessagingIdentityEventEntity;

import java.util.UUID;

public interface MessagingIdentityEventRepository extends JpaRepository<MessagingIdentityEventEntity, UUID> {
}
