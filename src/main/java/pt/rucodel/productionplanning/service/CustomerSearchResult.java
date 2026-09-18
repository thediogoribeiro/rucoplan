package pt.rucodel.productionplanning.service;

import pt.rucodel.productionplanning.entity.CustomerReferenceEntity;

public record CustomerSearchResult(
        CustomerReferenceEntity customer,
        double score,
        String distinction
) {
}
