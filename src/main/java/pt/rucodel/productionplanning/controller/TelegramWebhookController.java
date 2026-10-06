package pt.rucodel.productionplanning.controller;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.telegram.TelegramUpdate;
import pt.rucodel.productionplanning.telegram.TelegramUpdateProcessor;
import pt.rucodel.productionplanning.telegram.TelegramProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/integrations/telegram/webhook")
public class TelegramWebhookController {
    private final TelegramUpdateProcessor processor;
    private final String webhookSecret;

    public TelegramWebhookController(TelegramUpdateProcessor processor,
                                     TelegramProperties properties,
                                     Environment environment) {
        this.processor = processor;
        this.webhookSecret = properties.webhookSecret(environment.getProperty("app.environment", "local"));
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody TelegramUpdate update,
                                        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String suppliedSecret) {
        if (!validSecret(suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        processor.process(update);
        return ResponseEntity.ok().build();
    }

    private boolean validSecret(String suppliedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank() || suppliedSecret == null || suppliedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
