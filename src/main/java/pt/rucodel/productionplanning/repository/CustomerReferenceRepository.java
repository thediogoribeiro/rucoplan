package pt.rucodel.productionplanning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.rucodel.productionplanning.domain.ProductionSiteCode;
import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerReferenceRepository extends JpaRepository<CustomerReferenceEntity, UUID> {
    Optional<CustomerReferenceEntity> findByExternalId(String externalId);

    Optional<CustomerReferenceEntity> findByProductionSite_CodeAndExternalId(ProductionSiteCode siteCode, String externalId);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.id = :id
            """)
    Optional<CustomerReferenceEntity> findByIdForSite(@Param("siteCode") ProductionSiteCode siteCode, @Param("id") UUID id);

    Optional<CustomerReferenceEntity> findByCustomerNumber(Integer customerNumber);

    Optional<CustomerReferenceEntity> findByProductionSite_CodeAndCustomerNumber(ProductionSiteCode siteCode, Integer customerNumber);

    Optional<CustomerReferenceEntity> findByCustomerCode(String customerCode);

    Optional<CustomerReferenceEntity> findByProductionSite_CodeAndCustomerCode(ProductionSiteCode siteCode, String customerCode);

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

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.active = true
            order by c.name asc
            """)
    List<CustomerReferenceEntity> findActiveForSite(@Param("siteCode") ProductionSiteCode siteCode);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.active = true
              and c.normalizedName = :normalizedName
            order by c.name asc, c.id asc
            """)
    List<CustomerReferenceEntity> findActiveByNormalizedNameForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                     @Param("normalizedName") String normalizedName);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.active = true
              and c.taxIdentifier = :taxIdentifier
              and c.countryCode = :countryCode
            order by c.name asc, c.id asc
            """)
    List<CustomerReferenceEntity> findActiveByTaxIdentifierForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                    @Param("taxIdentifier") String taxIdentifier,
                                                                    @Param("countryCode") String countryCode);

    @Query("""
            select count(c) > 0 from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.active = true
              and c.normalizedName = :normalizedName
              and c.taxIdentifier = :taxIdentifier
              and (:excludedId is null or c.id <> :excludedId)
            """)
    boolean existsActiveByNormalizedNameAndTaxIdentifierForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                               @Param("normalizedName") String normalizedName,
                                                               @Param("taxIdentifier") String taxIdentifier,
                                                               @Param("excludedId") UUID excludedId);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.externalSystem = :externalSystem
              and c.externalCustomerId = :externalCustomerId
            """)
    Optional<CustomerReferenceEntity> findExternalReferenceForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                                    @Param("externalSystem") String externalSystem,
                                                                    @Param("externalCustomerId") String externalCustomerId);

    @Query("""
            select count(c) > 0 from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.externalSystem = :externalSystem
              and c.externalCustomerId = :externalCustomerId
            """)
    boolean existsExternalReferenceForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                           @Param("externalSystem") String externalSystem,
                                           @Param("externalCustomerId") String externalCustomerId);

    @Query("""
            select c from CustomerReferenceEntity c
            where c.productionSite.code = :siteCode
              and c.active = true
              and (
                lower(coalesce(c.externalId, '')) like lower(concat('%', :query, '%'))
                or lower(c.name) like lower(concat('%', :query, '%'))
              )
            order by c.name
            """)
    List<CustomerReferenceEntity> searchForSite(@Param("siteCode") ProductionSiteCode siteCode,
                                                @Param("query") String query);
}
