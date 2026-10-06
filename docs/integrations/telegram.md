# Telegram bots por ambiente

O RucoPlan usa dois bots Telegram distintos:

- bot de testes: usado apenas localmente;
- bot de produção: usado apenas no Heroku.

O fluxo de perguntas, estados, validações, criação de pedidos e regras PT/LUX é comum. Só muda a configuração de ligação ao Telegram.

## Endpoint

```text
POST /api/v1/integrations/telegram/webhook
```

O backend valida o header `X-Telegram-Bot-Api-Secret-Token` com o segredo do ambiente ativo.

## Variáveis locais

O ficheiro `.env` local deve conter apenas credenciais do bot de testes:

```properties
APP_ENV=local
APP_ENVIRONMENT=local
TELEGRAM_ENABLED=true
TELEGRAM_TEST_BOT_TOKEN=<token-do-bot-de-testes>
TELEGRAM_TEST_BOT_USERNAME=<username-do-bot-de-testes>
TELEGRAM_TEST_WEBHOOK_SECRET=<secret-local>
TELEGRAM_TEST_WEBHOOK_URL=https://<subdominio-ngrok>
TELEGRAM_WEBHOOK_AUTO_REGISTER=false
```

Não guardar credenciais de produção no `.env` local.

## Heroku Config Vars

Produção deve ser configurada apenas por Config Vars:

```bash
heroku config:set \
  SPRING_PROFILES_ACTIVE=production \
  APP_ENV=production \
  APP_ENVIRONMENT=production \
  TELEGRAM_ENABLED=true \
  TELEGRAM_PRODUCTION_BOT_TOKEN='<token-do-bot-de-producao>' \
  TELEGRAM_PRODUCTION_BOT_USERNAME='<username-do-bot-de-producao>' \
  TELEGRAM_PRODUCTION_WEBHOOK_SECRET='<secret-producao>' \
  TELEGRAM_PRODUCTION_WEBHOOK_URL='https://<nome-app>.herokuapp.com' \
  TELEGRAM_WEBHOOK_AUTO_REGISTER=true \
  --app <nome-da-app>
```

Verificação sem imprimir tokens em tickets ou logs:

```bash
heroku config --app <nome-da-app>
```

## Configurar webhooks

Bot local com ngrok:

```bash
curl -X POST \
  "https://api.telegram.org/bot<TOKEN_DO_BOT_TESTE>/setWebhook" \
  -d "url=https://<SUBDOMINIO_NGROK>/api/v1/integrations/telegram/webhook" \
  -d "secret_token=<SECRET_LOCAL>"
```

Bot de produção no Heroku:

```bash
curl -X POST \
  "https://api.telegram.org/bot<TOKEN_DO_BOT_PRODUCAO>/setWebhook" \
  -d "url=https://<NOME_APP>.herokuapp.com/api/v1/integrations/telegram/webhook" \
  -d "secret_token=<SECRET_PRODUCAO>"
```

Consultar o webhook:

```bash
curl "https://api.telegram.org/bot<TOKEN_DO_BOT_PRODUCAO>/getWebhookInfo"
```

Como são bots diferentes, os dois webhooks podem estar ativos ao mesmo tempo.

## Seleção do bot

A aplicação usa `APP_ENV` primeiro e `APP_ENVIRONMENT` como fallback.

- `production` seleciona `TELEGRAM_PRODUCTION_*`;
- qualquer outro valor seleciona `TELEGRAM_TEST_*`.

Se `TELEGRAM_ENABLED=true` e faltar token, username ou secret do ambiente ativo, a aplicação falha no arranque com uma mensagem sanitizada. Não existe fallback de produção para teste nem de teste para produção.

O log seguro esperado é:

```text
telegram.configuration.loaded environment=production bot=production
```

ou:

```text
telegram.configuration.loaded environment=local bot=test
```

## Diagnóstico

- `401` no webhook: confirmar `secret_token` configurado no Telegram e Config Var correspondente.
- Sem mensagens: confirmar `getWebhookInfo`, URL pública HTTPS e `TELEGRAM_ENABLED=true`.
- Bot errado: confirmar `APP_ENV`, `APP_ENVIRONMENT` e o log `telegram.configuration.loaded`.
- Dados no ambiente errado: confirmar que o bot de teste aponta para ngrok/local e o bot de produção aponta para Heroku.

## Rotação de token

Se um token for exposto:

1. revogar/rodar o token no BotFather;
2. atualizar `.env` apenas se for token de teste;
3. atualizar Heroku Config Vars apenas se for token de produção;
4. voltar a executar `setWebhook`;
5. confirmar com `getWebhookInfo`;
6. procurar e remover qualquer exposição em logs, tickets ou ficheiros locais.
