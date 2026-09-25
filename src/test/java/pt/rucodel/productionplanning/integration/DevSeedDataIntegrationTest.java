package pt.rucodel.productionplanning.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.seed.enabled=true")
@ActiveProfiles({"test", "dev"})
class DevSeedDataIntegrationTest {
    @jakarta.annotation.Resource WheelIntakeRequestRepository requests;

    @Test
    void developmentSeedCreatesRequestsWithPublicCodes() {
        assertThat(requests.findAll())
                .hasSize(6)
                .allSatisfy(request -> assertThat(request.getRequestCode())
                        .startsWith("REQ-")
                        .hasSizeLessThanOrEqualTo(15));
    }
}
