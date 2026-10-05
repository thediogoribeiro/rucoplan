# Novo Pedido Manual

O separador **Novo Pedido** permite registar pedidos no painel administrativo quando:

- O cliente aparece diretamente na fábrica;
- Telegram ou WhatsApp não estão disponíveis;
- Um funcionário precisa de registar um pedido sem motorista.

## Diferença face a Telegram/WhatsApp

Telegram e WhatsApp comunicam pedidos através de conversas com motorista identificado. O pedido manual é criado por um utilizador administrativo e pode não ter motorista.

## Cliente

O formulário permite selecionar um cliente existente ou criar um novo cliente com:

- Nome;
- NIF/VAT;
- País;
- Localidade;
- ID RucoFi opcional.

O campo **País** é texto editável e guarda o nome por extenso introduzido pelo utilizador, por exemplo `Portugal`, `Luxemburgo` ou `França`. O valor sugerido vem da unidade ativa: Portugal para `PT` e Luxemburgo para `LUX`. O utilizador pode alterar a sugestão antes de guardar.

`country_code` permanece um campo técnico opcional para compatibilidade com dados e integrações existentes; o formulário administrativo não obriga o utilizador a escrever códigos ISO.

O código RucoPlan do cliente é gerado automaticamente.

## Motorista

O motorista é opcional. Não é criado nenhum motorista fictício para clientes diretos.

## Estados iniciais

Se as jantes já estão na fábrica:

```text
source = MANUAL
status = AT_FACTORY
arrival_confirmation_source = MANUAL_FACTORY_INTAKE
```

Se ainda não chegaram:

```text
source = MANUAL
status = COMMUNICATED
```

Nesse caso o pedido aparece em **Entrada na Fábrica** até a chegada ser confirmada.

## Planeamento

Todos os pedidos manuais usam o mesmo modelo, targets, calendário, prazos, fecho de turno e reabertura que os pedidos Telegram e WhatsApp.

## Auditoria

O backend regista quem criou o pedido, quando, quantidades, cliente, motorista opcional, estado inicial e origem.

O histórico de estados (`request_status_history`) referencia sempre um `app_user` válido quando a ação vem de uma pessoa autenticada. Em autenticação local, se o utilizador local não existir em `app_user`, a aplicação cria uma identidade de auditoria inativa com o mesmo username para cumprir a foreign key sem permitir login por essa conta. Ações automáticas ou de canais como Telegram/WhatsApp usam atores técnicos inativos próprios.

Ao confirmar chegada em **Entrada na Fábrica**, o frontend aguarda a resposta do backend, bloqueia o botão durante a operação e recarrega os dados autoritativos. Em sucesso, o pedido sai de **Entrada na Fábrica**, continua em **Pedidos** e o plano diário passa a usar o estado persistido. Em erro, o pedido permanece visível e a mensagem inclui o correlation ID quando fornecido pela API.

No **Fecho do Turno**, cada fecho parcial ou total também aguarda o backend e recarrega os dados autoritativos. Fechos totais removem a linha dos pendentes; fechos parciais mostram apenas a quantidade restante. O botão fica bloqueado durante a chamada para evitar duplo clique.

## Endpoint

```text
POST /api/v1/admin/requests/manual
```

O backend calcula o total a partir das quantidades por tipo e rejeita total zero, tipos repetidos e datas incoerentes.

## Teste rápido

1. Abrir `/admin.html`.
2. Entrar como administrador.
3. Abrir **Novo Pedido**.
4. Selecionar ou criar cliente.
5. Deixar motorista vazio, se aplicável.
6. Indicar quantidades.
7. Escolher se as jantes já estão na fábrica.
8. Criar o pedido.
9. Confirmar presença em **Pedidos**, **Entrada na Fábrica** ou no plano, conforme o estado inicial.
