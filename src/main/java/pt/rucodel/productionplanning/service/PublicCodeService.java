package pt.rucodel.productionplanning.service;

import org.springframework.stereotype.Service;
import pt.rucodel.productionplanning.repository.WheelIntakeRequestRepository;

import java.security.SecureRandom;

@Service
public class PublicCodeService {
    private static final char[] REQUEST_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final WheelIntakeRequestRepository requests;

    public PublicCodeService(WheelIntakeRequestRepository requests) {
        this.requests = requests;
    }

    public String driverCode(int number) {
        return "MOTOR-" + String.format("%03d", number);
    }

    public String customerCode(int number) {
        return "CLI-" + String.format("%03d", number);
    }

    public String newRequestCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "REQ-" + randomSuffix(11);
            if (!requests.existsByRequestCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique request code.");
    }

    private String randomSuffix(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(REQUEST_ALPHABET[random.nextInt(REQUEST_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
