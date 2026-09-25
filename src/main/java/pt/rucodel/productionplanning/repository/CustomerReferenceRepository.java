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

    Optional<CustomerReferenceEntity> findByCustomerNumber(Integer customerNumber);

    Optional<CustomerReferenceEntity> findByCustomerCode(String customerCode);

    List<CustomerReferenceEntity> findByNameIgnoreCase(String name);

    List<CustomerReferenceEntity> findByActiveTrueAndNormalizedNameOrderByNameAscIdAsc(String normalizedName);

    List<CustomerReferenceEntity> findByActiveTrueAndTaxIdentifierAndCountryCodeOrderByNameAscIdAsc(String taxIdentifier, String countryCode);

    boolean existsByExternalSystemAndExternalCustomerId(String externalSystem, String externalCustomerId);

    Optional<CustomerReferenceEntity> findByExternalSystemAndExternalCustomerId(String externalSystem, String externalCustomerId);

    @Query(value = "select nextval('customer_number_seq')", nativeQuery = true)
    Integer nextCustomerNumber();

    @Query(value = "select nextval('customer_code_seq')", nativeQuery = true)
    Integer nextCustomerCodeNumber();

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
