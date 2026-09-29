package pt.rucodel.productionplanning.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.whatsapp.WhatsAppProperties;
import pt.rucodel.productionplanning.whatsapp.WhatsAppUpdateProcessor;
import pt.rucodel.productionplanning.whatsapp.WhatsAppWebhookSignatureVerifier;
import pt.rucodel.productionplanning.whatsapp.WhatsAppWebhookVerificationService;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp/webhook")
public class WhatsAppWebhookController {
    private final WhatsAppWebhookVerificationService verificationService;
    private final WhatsAppWebhookSignatureVerifier signatureVerifier;
    private final WhatsAppUpdateProcessor processor;
    private final WhatsAppProperties properties;

    public WhatsAppWebhookController(WhatsAppWebhookVerificationService verificationService,
                                     WhatsAppWebhookSignatureVerifier signatureVerifier,
                                     WhatsAppUpdateProcessor processor,
                                     WhatsAppProperties properties) {
        this.verificationService = verificationService;
        this.signatureVerifier = signatureVerifier;
        this.processor = processor;
        this.properties = properties;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (challenge != null && verificationService.canVerify(mode, verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawPayload,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (!properties.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!signatureVerifier.isValid(rawPayload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        processor.process(rawPayload);
        return ResponseEntity.ok().build();
    }
}
