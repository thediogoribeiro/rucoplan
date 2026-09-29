# Production Sites

RucoPlan suporta duas unidades de producao na mesma aplicacao, no mesmo deployment e na mesma base de dados:

- `PT` - Portugal - `Europe/Lisbon`
- `LUX` - Luxemburgo - `Europe/Luxembourg`

O site de producao identifica onde o trabalho sera planeado e produzido. Nao representa o pais da morada do cliente. Um cliente frances, belga ou alemao pode pertencer ao site Luxemburgo.

## Modelo

A tabela `production_site` guarda os sites ativos. As entidades operacionais guardam uma referencia obrigatoria ao site:

- Clientes;
- Pedidos;
- Targets;
- Planos de producao;
- Linhas de plano;
- Fechos/reconciliacoes;
- Alertas;
- Auditoria operacional;
- Rascunhos e conversas de intake.

Os motoristas continuam globais e podem ser associados a um ou aos dois sites atraves de `driver_production_site`.

Os utilizadores administrativos podem ter acesso a um ou aos dois sites atraves de `application_user_site`.

## Login

O login inclui a unidade de producao pretendida. O backend valida:

1. Credenciais do utilizador;
2. Site existente e ativo;
3. Associacao ativa do utilizador ao site.

Depois do login, o token contem o site ativo. As operacoes administrativas usam esse site autenticado como fonte de verdade. O frontend mostra a unidade ativa no cabecalho. Para mudar de site, o utilizador deve terminar sessao e entrar novamente.

## Isolamento

As APIs administrativas nao devem aceitar um parametro livre de site como autorizacao. O site vem da sessao autenticada.

Uma sessao Portugal so consulta e altera dados Portugal. Uma sessao Luxemburgo so consulta e altera dados Luxemburgo.

Se um ID de outro site for enviado manualmente, a resposta deve ser segura, normalmente `404` ou erro de dominio sem revelar dados do outro site.

## Clientes

Cada cliente pertence a um unico site. O mesmo nome, NIF/VAT ou ID externo pode existir em Portugal e no Luxemburgo como registos tecnicos diferentes.

As pesquisas exatas e fuzzy incluem sempre o site na query. Caches de clientes e sugestoes tambem devem incluir o site na chave.

## Pedidos

Cada pedido pertence a um unico site e deve referenciar um cliente do mesmo site. O site de um pedido confirmado nao deve ser alterado por fluxos normais.

Pedidos manuais sao criados no site da sessao ativa. Telegram e WhatsApp perguntam primeiro a unidade de producao e guardam essa escolha no rascunho.

## Motoristas

O motorista e global. A associacao ao site e feita por `driver_production_site`.

Um motorista pode trabalhar apenas em Portugal, apenas no Luxemburgo ou nos dois sites. Escolher um site no Telegram ou WhatsApp cria a associacao de forma idempotente quando ela ainda nao existe.

## Telegram e WhatsApp

Um novo pedido nos canais conversacionais pergunta primeiro:

```text
Para qual unidade de producao e este pedido?

1 - Portugal
2 - Luxemburgo
```

A escolha limita a pesquisa de clientes, a criacao de cliente, o rascunho, o pedido final e o recalculo do plano.

Se o site do rascunho for alterado antes da confirmacao, o cliente previamente escolhido deve ser limpo para evitar misturar cliente Portugal num pedido Luxemburgo ou o contrario.

## Targets

Targets sao independentes por site.

Portugal preserva os targets existentes e os defaults operacionais `150/180`.

Luxemburgo nao herda os targets de Portugal automaticamente. Enquanto nao existir configuracao LUX, o sistema deve apresentar o estado "Targets por configurar" e impedir planos definitivos que dependam desses targets.

## Planeamento

O motor de planeamento e unico, mas todas as entradas recebem explicitamente o site.

O planeamento Portugal usa apenas:

- Pedidos Portugal;
- Clientes Portugal;
- Targets Portugal;
- Fechos Portugal;
- Carry-over Portugal;
- Alertas Portugal.

O Luxemburgo usa o mesmo motor e as mesmas regras, mas apenas com os dados Luxemburgo.

Planos de Portugal e Luxemburgo podem existir para a mesma data porque a unicidade e por `(production_site_id, planning_date)`.

## Timezones

O timezone vem do site:

- Portugal: `Europe/Lisbon`
- Luxemburgo: `Europe/Luxembourg`

O timezone do site deve ser usado em conversas, janelas horarias, calculo do dia atual, jobs, fechos, prazos e apresentacao administrativa.

## Jobs

Jobs automaticos devem iterar por site ativo. Uma falha num site nao deve impedir a execucao do outro.

Os logs dos jobs devem incluir:

- Site;
- Inicio e fim;
- Resultado;
- Trigger;
- Correlation ID.

## SSE

O endpoint SSE respeita o site autenticado. Uma sessao Portugal recebe apenas eventos Portugal. Uma sessao Luxemburgo recebe apenas eventos Luxemburgo.

O filtro deve acontecer no backend, antes de enviar eventos ao browser.

## Auditoria

Eventos operacionais devem guardar o `production_site_id` quando a acao pertence a um site. Eventos tecnicos globais devem ser identificados como globais.

Na pagina de auditoria, a vista por defeito mostra apenas o site ativo.

## Migrations

A migration multi-site assume que os dados historicos existentes pertencem a Portugal:

1. Cria `PT` e `LUX`;
2. Adiciona colunas de site;
3. Atribui PT a dados existentes;
4. Associa utilizadores e motoristas existentes a PT;
5. Torna as colunas operacionais obrigatorias;
6. Cria indices, constraints e unicidades por site.

Nao ha atribuicao automatica de dados existentes ao Luxemburgo.

Se uma migration ja tiver sido aplicada localmente e o ficheiro for alterado durante desenvolvimento, a base local pode necessitar de `flyway repair` ou recriacao controlada. Nao aplicar isso em producao sem processo de migracao formal.

## Adicionar um novo site

Para adicionar um site futuro:

1. Criar registo em `production_site`;
2. Configurar timezone;
3. Criar acessos em `application_user_site`;
4. Associar motoristas conforme necessario;
5. Configurar targets iniciais;
6. Rever jobs, caches, diagnostico e testes;
7. Garantir que pesquisas, planos, fechos e auditoria continuam site-scoped.

## Testes

Executar:

```bash
mvn test
```

Os testes de migration PostgreSQL dependem de Docker/Testcontainers.
