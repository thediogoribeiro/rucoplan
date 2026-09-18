package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerReferenceRepository extends JpaRepository<CustomerReferenceEntity, UUID> {
    Optional<CustomerReferenceEntity> findByExternalId(String externalId);

    List<CustomerReferenceEntity> findByNameIgnoreCase(String name);

    List<CustomerReferenceEntity> findByActiveTrueAndNormalizedNameOrderByNameAscIdAsc(String normalizedName);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.active = true
              and (
                lower(coalesce(c.externalId, '')) like lower(concat('%', :query, '%'))
                or lower(c.name) like lower(concat('%', :query, '%'))
              )
            order by c.name
            """)
    List<CustomerReferenceEntity> search(@Param("query") String query);

    List<CustomerReferenceEntity> findByActiveTrueOrderByName();
}
