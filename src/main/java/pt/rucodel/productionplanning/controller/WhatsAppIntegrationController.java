package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.WhatsAppIngestionResponse;
import pt.rucodel.productionplanning.dto.WhatsAppIntakeRequest;
import pt.rucodel.productionplanning.domain.IngestionStatus;
import pt.rucodel.productionplanning.service.WhatsAppIngestionService;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp/requests")
public class WhatsAppIntegrationController {
    private final WhatsAppIngestionService ingestionService;

    public WhatsAppIntegrationController(WhatsAppIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<WhatsAppIngestionResponse> ingest(@Valid @RequestBody WhatsAppIntakeRequest request,
                                                            @RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @RequestHeader(value = "X-Integration-Token", required = false) String integrationToken) {
        WhatsAppIngestionResponse response = ingestionService.ingest(request, authorization == null ? integrationToken : authorization);
        HttpStatus status = response.status() == IngestionStatus.DUPLICATE ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
