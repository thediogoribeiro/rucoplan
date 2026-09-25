package pt.rucodel.productionplanning.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryLockingStaticTest {
    @Test
    void pessimisticLockedMessagingIdentityLookupDoesNotFetchJoinDriver() throws Exception {
        String repository = Files.readString(Path.of(
                "src/main/java/pt/rucodel/productionplanning/repository/MessagingIdentityRepository.java"));

        String lockedLookup = repository.substring(
                repository.indexOf("@Lock(LockModeType.PESSIMISTIC_WRITE)"),
                repository.indexOf("List<MessagingIdentityEntity> findByDriverId"));

        assertThat(lockedLookup).contains("@Lock(LockModeType.PESSIMISTIC_WRITE)");
        assertThat(lockedLookup).doesNotContain("join fetch");
    }
}
