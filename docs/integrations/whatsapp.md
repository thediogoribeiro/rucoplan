# Integração WhatsApp Business Platform

## Visão geral

O RucoPlan usa a WhatsApp Business Platform Cloud API oficial da Meta. O fluxo é:

```text
Webhook Meta
→ /api/v1/integrations/whatsapp/webhook
→ validação X-Hub-Signature-256
→ whatsapp_ingestion_item
→ messaging_identity(channel=WHATSAPP)
→ whatsapp_conversation
→ WheelIntakeRequestService
→ planeamento
→ SSE/REST administrativo
```

Não é usado WhatsApp Web, QR Code, Selenium, cookies pessoais ou bibliotecas que simulem dispositivo.

## Pré-requisitos Meta

- Meta app com produto WhatsApp configurado.
- WhatsApp Business Account.
- Phone Number ID registado.
- Token com permissão `whatsapp_business_messaging`.
- Callback HTTPS público.
- Subscrição dos eventos de mensagens.

Documentação oficial:

- Cloud API: https://developers.facebook.com/docs/whatsapp/cloud-api
- Webhooks Graph API: https://developers.facebook.com/docs/graph-api/webhooks/getting-started
- Messages endpoint: https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages
- Templates: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-message-templates

## Variáveis

```text
WHATSAPP_ENABLED=true
WHATSAPP_ACCESS_TOKEN=...
WHATSAPP_APP_SECRET=...
WHATSAPP_VERIFY_TOKEN=...
WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_BUSINESS_ACCOUNT_ID=...
WHATSAPP_GRAPH_API_VERSION=v26.0
WHATSAPP_WEBHOOK_PUBLIC_URL=https://.../api/v1/integrations/whatsapp/webhook
```

Segredos ficam apenas em variáveis de ambiente. O `.env.example` usa placeholders.

## Webhook

Verificação:

```text
GET /api/v1/integrations/whatsapp/webhook
```

O backend valida `hub.mode=subscribe`, `hub.verify_token` e devolve `hub.challenge`.

Receção:

```text
POST /api/v1/integrations/whatsapp/webhook
```

O backend valida `X-Hub-Signature-256` usando HMAC-SHA256 com `WHATSAPP_APP_SECRET`, persiste a mensagem e processa a conversa de forma idempotente.

## Conversa

O WhatsApp usa a mesma sequência operacional do Telegram:

1. Nome do motorista, se a identidade ainda não estiver associada.
2. Cliente.
3. Sugestões ou criação de cliente.
4. Bipartidas.
5. Lavadas.
6. Normais.
7. Data e intervalo de entrada.
8. Data e intervalo de levantamento.
9. Notas.
10. Confirmação.

Ao confirmar, o pedido é criado como:

```text
source = WHATSAPP
lifecycle_status = COMMUNICATED
```

A chegada física continua a ser confirmada em Entrada na Fábrica.

## Janela e templates

O fluxo principal é iniciado pelo motorista, dentro da janela de atendimento aberta pelo utilizador. O RucoPlan não envia mensagens proativas fora desta janela sem templates aprovados. Configure nomes de templates fora do código quando forem necessários.

## Scripts

```bash
scripts/whatsapp/check-config.sh
scripts/whatsapp/check-webhook.sh
scripts/whatsapp/send-test-message.sh <wa-id> [mensagem]
scripts/whatsapp/replay-webhook-fixture.sh fixture.json [url]
```

Os scripts não imprimem tokens. O envio de mensagem real pede confirmação.

## Diagnóstico

Em `Definições > Diagnóstico do Sistema`, a secção WhatsApp mostra configuração presente/ausente, último webhook, última mensagem processada, pendentes e último erro sanitizado.

## Troubleshooting

- `403` no GET: verify token ou `hub.mode` incorreto.
- `401` no POST: assinatura inválida ou app secret incorreto.
- Sem envio: confirmar `WHATSAPP_ACCESS_TOKEN` e `WHATSAPP_PHONE_NUMBER_ID`.
- Sem webhooks: confirmar subscrição do campo de mensagens na app Meta e URL HTTPS pública.

## Desligar

Defina:

```text
WHATSAPP_ENABLED=false
```

O Telegram e o frontend administrativo continuam independentes.

## Testes

```bash
mvn test
node --check src/main/resources/static/js/admin.js
```

Para testar payloads locais, use `replay-webhook-fixture.sh` com uma fixture sanitizada.

## Limitações

- Só texto e respostas interativas simples são processados nesta fase.
- Templates proativos não são enviados até existirem templates aprovados configurados.
- Associação entre identidades Telegram e WhatsApp do mesmo motorista continua explícita e auditada.
