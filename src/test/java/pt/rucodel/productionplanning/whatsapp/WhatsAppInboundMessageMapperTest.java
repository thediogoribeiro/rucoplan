package pt.rucodel.productionplanning.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppInboundMessageMapperTest {

    private final WhatsAppInboundMessageMapper mapper = new WhatsAppInboundMessageMapper(new ObjectMapper());

    @Test
    void mapsTextMessagesFromMetaWebhookPayload() {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba-123",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {
                              "phone_number_id": "phone-456"
                            },
                            "contacts": [
                              {
                                "profile": { "name": "Joao Motorista" },
                                "wa_id": "351910000000"
                              }
                            ],
                            "messages": [
                              {
                                "from": "351910000000",
                                "id": "wamid.HBgLMzUx",
                                "timestamp": "1790600000",
                                "type": "text",
                                "text": { "body": "teste" }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        assertThat(mapper.map(payload))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.externalMessageId()).isEqualTo("wamid.HBgLMzUx");
                    assertThat(message.fromWaId()).isEqualTo("351910000000");
                    assertThat(message.phoneNumber()).isEqualTo("351910000000");
                    assertThat(message.profileName()).isEqualTo("Joao Motorista");
                    assertThat(message.phoneNumberId()).isEqualTo("phone-456");
                    assertThat(message.whatsappBusinessAccountId()).isEqualTo("waba-123");
                    assertThat(message.messageType()).isEqualTo("text");
                    assertThat(message.text()).isEqualTo("teste");
                    assertThat(message.receivedAt()).isEqualTo(OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochSecond(1790600000L), ZoneOffset.UTC));
                });
    }

    @Test
    void mapsInteractiveRepliesToTextFallback() {
        String payload = """
                {
                  "entry": [
                    {
                      "id": "waba-123",
                      "changes": [
                        {
                          "value": {
                            "metadata": { "phone_number_id": "phone-456" },
                            "messages": [
                              {
                                "from": "351910000000",
                                "id": "wamid.button",
                                "type": "interactive",
                                "interactive": {
                                  "button_reply": {
                                    "id": "1",
                                    "title": "Confirmar"
                                  }
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        assertThat(mapper.map(payload))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.externalMessageId()).isEqualTo("wamid.button");
                    assertThat(message.text()).isEqualTo("1");
                });
    }
}
