package pt.rucodel.productionplanning.service;

public record WhatsAppIdentitySnapshot(
        String waId,
        String phoneNumber,
        String profileName,
        String phoneNumberId
) {
}
