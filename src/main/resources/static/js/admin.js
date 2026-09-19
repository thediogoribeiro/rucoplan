const state = {
  date: pp.tomorrowString(),
  view: location.hash.replace('#', '') || 'planning',
  dashboard: null,
  productionPlan: null,
  productionPlans: [],
  planningTargets: [],
  settings: null,
  drivers: [],
  messagingIdentities: [],
  customerRegistrationRequests: [],
  customers: [],
  requests: [],
  audit: [],
  capacityAlerts: [],
  busy: {},
  stream: null,
  fallbackTimer: null,
  filters: {
    driver: '',
    customer: '',
    status: '',
    risk: '',
    confidence: ''
  }
};

document.addEventListener('DOMContentLoaded', async () => {
  const current = api.requireRole('ADMIN');
  if (!current) return;
  document.querySelector('#logout').addEventListener('click', api.logout);
  document.querySelector('#dashboard-date').value = state.date;
  document.querySelector('#dashboard-date').addEventListener('change', async event => {
    state.date = event.target.value || pp.todayString();
    await loadAdminData();
    render();
  });
  document.querySelector('#today-button').addEventListener('click', async () => {
    state.date = pp.todayString();
    document.querySelector('#dashboard-date').value = state.date;
    await loadAdminData();
    render();
  });
  document.querySelector('#regenerate-button').addEventListener('click', regeneratePlan);
  document.querySelector('#print-button').addEventListener('click', () => window.open(`print-plan.html?date=${encodeURIComponent(state.date)}`, '_blank'));
  window.addEventListener('hashchange', () => {
    state.view = location.hash.replace('#', '') || 'dashboard';
    render();
  });
  document.querySelectorAll('.nav a').forEach(link => {
    link.addEventListener('click', () => {
      document.querySelectorAll('.nav a').forEach(item => item.classList.toggle('active', item === link));
    });
  });
  connectStream();
  startFallbackPolling();
  await loadAdminData();
  render();
});

async function loadAdminData() {
  const from = pp.addDays(state.date, -2);
  const to = pp.addDays(state.date, 7);
  const [dashboard, productionPlan, productionPlans, planningTargets, settings, drivers, messagingIdentities, customerRegistrationRequests, customers, requests, audit] = await Promise.all([
    api.get(`/api/v1/admin/dashboard?date=${state.date}`),
    api.get(`/api/v1/admin/production-plans/${state.date}`),
    api.get(`/api/v1/admin/production-plans?from=${from}&to=${to}`),
    api.get('/api/v1/admin/planning-targets'),
    api.get(`/api/v1/admin/settings/daily?date=${state.date}`),
    api.get('/api/v1/admin/drivers'),
    api.get('/api/v1/admin/messaging-identities'),
    api.get('/api/v1/admin/customer-registration-requests'),
    api.get('/api/v1/admin/customers'),
    api.get('/api/v1/admin/requests?size=200'),
    api.get('/api/v1/admin/audit?size=50')
  ]);
  state.dashboard = dashboard;
  state.productionPlan = productionPlan;
  state.productionPlans = productionPlans || [];
  state.planningTargets = planningTargets || [];
  state.settings = settings;
  state.drivers = drivers;
  state.messagingIdentities = messagingIdentities || [];
  state.customerRegistrationRequests = customerRegistrationRequests || [];
  state.customers = customers;
  state.requests = requests.content || [];
  state.audit = audit.content || [];
  state.capacityAlerts = [];
}

function render() {
  if (state.view === 'planning') renderProductionPlanning();
  else if (state.view === 'closure') renderShiftClosure();
  else if (state.view === 'targets') renderTargets();
  else if (state.view === 'settings') renderSettings();
  else if (state.view === 'requests') renderRequests();
  else if (state.view === 'drivers') renderDrivers();
  else if (state.view === 'customers') renderCustomers();
  else if (state.view === 'audit') renderAudit();
  else renderDashboard();
}

function renderProductionPlanning() {
  const plan = state.productionPlan;
  const lines = plan?.lines || [];
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header">
        <div>
          <h2>${plan ? pp.fullDate(plan.planningDate) : 'Planeamento de Produção'} ${plan ? pp.badge(plan.status) : ''}</h2>
          ${targetsOutdated(plan) ? '<p class="muted">Existem targets mais recentes. Clique em Recalcular para atualizar o plano.</p>' : ''}
        </div>
        <div class="actions">
          <button type="button" class="secondary" data-date-nav="-1">Dia anterior</button>
          <button type="button" class="secondary" data-date-set="${pp.todayString()}">Hoje</button>
          <button type="button" class="secondary" data-date-set="${pp.tomorrowString()}">Amanhã</button>
          <button type="button" class="secondary" data-date-nav="1">Dia seguinte</button>
          <button id="planning-recalculate" type="button" ${state.busy.recalculate ? 'disabled' : ''}>${state.busy.recalculate ? 'A recalcular...' : 'Recalcular'}</button>
        </div>
      </div>
      ${plan ? dailyHeader(plan) : '<p class="muted">Sem plano para a data selecionada.</p>'}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Trabalho por cliente e pedido</h2></div>
      ${productionPlanTable(lines)}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Dias próximos</h2></div>
      <div class="window-grid">${state.productionPlans.map(planCard).join('')}</div>
    </section>`;
  bindPlanningNavigation();
}

function dailyHeader(plan) {
  return `<section class="metric-grid metric-grid-primary">
    ${metric('Total planeado', `${plan.totalPlanned} jantes`)}
    ${metric('Bipartidas', quantityValue(plan.wheelQuantities, 'BIPARTITE'))}
    ${metric('Lavadas', quantityValue(plan.wheelQuantities, 'WASHED'))}
    ${metric('Normais', quantityValue(plan.wheelQuantities, 'NORMAL'))}
    ${metric('Total concluído', plan.totalCompleted)}
    ${overtimeMetric(plan)}
  </section>
  <section class="metric-grid metric-grid-secondary">
    ${metric('Target mínimo diário', plan.minimumDailyTarget)}
    ${metric('Target máximo diário — capacidade regular', plan.regularDailyCapacity)}
    ${metric('Trabalho transportado', plan.carriedOverQuantity)}
    ${metric('Antecipado de dias futuros', plan.advancedQuantity)}
    ${metric('Quantidade em risco', plan.atRiskQuantity)}
    ${metric('Diferença', plan.differenceToMinimum)}
    ${metric('Última atualização', pp.formatDateTime(plan.generatedAt))}
  </section>
  ${plan.warning ? `<p class="message ${plan.overtimeRequired ? 'error' : 'warning'}">${pp.escapeHtml(plan.warning)}</p>` : ''}`;
}

function overtimeMetric(plan) {
  const label = plan.overtimeRequired ? 'SIM' : 'NÃO';
  const detail = plan.overtimeRequired ? `Excesso estimado: ${plan.overtimeQuantity} jantes` : 'Dentro do target máximo';
  return `<div class="metric overtime ${plan.overtimeRequired ? 'yes' : 'no'}">
    <span>Horas extra</span>
    <strong>${label}</strong>
    <small>${pp.escapeHtml(detail)}</small>
  </div>`;
}

function productionPlanTable(lines) {
  if (!lines.length) return '<p class="muted">Sem trabalho planeado para este dia.</p>';
  const grouped = new Map();
  for (const line of lines) {
    grouped.set(line.customerName, [...(grouped.get(line.customerName) || []), line]);
  }
  return [...grouped.entries()].map(([customer, customerLines]) => `
    <h3>${pp.escapeHtml(customer)}</h3>
    <div class="table-wrap"><table>
      <thead><tr><th>Ordem</th><th>Pedido</th><th>Motorista</th><th>Total pedido</th><th>Tipos de jantes</th><th>Planeado</th><th>Concluído</th><th>Restante</th><th>Entrada fábrica</th><th>Prazo</th><th>Levantamento</th><th>Notas</th><th>Origem</th><th>Indicações</th><th>Estado</th></tr></thead>
      <tbody>${customerLines.map(line => `<tr>
        <td>${line.priorityOrder}</td>
        <td><span class="code-pill">${pp.escapeHtml(line.requestCode || '-')}</span></td>
        <td>${pp.escapeHtml(line.driverName)}</td>
        <td>${line.requestTotalQuantity}</td>
        <td>${wheelSummaryVertical(line.wheelQuantities)}</td>
        <td>${line.plannedQuantity}</td>
        <td>${line.completedQuantity}</td>
        <td>${line.remainingQuantity}</td>
        <td>${factoryWindow(line.factoryDropoffStart, line.factoryDropoffEnd)}</td>
        <td>${pp.formatDateTime(line.deadlineAt)}</td>
        <td>${factoryWindow(line.factoryPickupStart, line.factoryPickupEnd)}</td>
        <td>${pp.escapeHtml(line.notes || '-')}</td>
        <td>${line.source === 'TELEGRAM' ? 'Telegram' : 'Aplicação'}</td>
        <td>${line.carriedOver ? pp.badge('CARRIED_OVER') : ''} ${line.advancedFromFuture ? pp.badge('ADVANCED') : ''}</td>
        <td>${pp.badge(line.status)} ${pp.badge(line.riskClassification)}</td>
      </tr>`).join('')}</tbody>
    </table></div>`).join('');
}

function planCard(plan) {
  return `<article class="window-column">
    <h3>${pp.formatDate(plan.planningDate)}</h3>
    <p>${pp.badge(plan.status)} ${plan.overtimeRequired ? pp.badge('OVERTIME_REQUIRED') : ''}</p>
    <p>Total: ${plan.totalPlanned} jantes · ${wheelSummary(plan.wheelQuantities)}</p>
    <p>${plan.totalCompleted} concluídas · ${plan.totalRemaining} pendentes</p>
    <p class="muted">Mín. ${plan.minimumDailyTarget} · Máx. ${plan.regularDailyCapacity}</p>
  </article>`;
}

function bindPlanningNavigation() {
  document.querySelectorAll('[data-date-nav]').forEach(button => button.addEventListener('click', async () => {
    state.date = pp.addDays(state.date, Number(button.dataset.dateNav));
    document.querySelector('#dashboard-date').value = state.date;
    await loadAdminData();
    render();
  }));
  document.querySelectorAll('[data-date-set]').forEach(button => button.addEventListener('click', async () => {
    state.date = button.dataset.dateSet;
    document.querySelector('#dashboard-date').value = state.date;
    await loadAdminData();
    render();
  }));
  document.querySelector('#planning-recalculate')?.addEventListener('click', async () => {
    state.busy.recalculate = true;
    renderProductionPlanning();
    try {
      state.productionPlan = await api.postJson(`/api/v1/admin/production-plans/${state.date}/recalculate`, {});
      state.productionPlans = await api.get(`/api/v1/admin/production-plans?from=${pp.addDays(state.date, -2)}&to=${pp.addDays(state.date, 7)}`) || [];
    } finally {
      state.busy.recalculate = false;
    }
    render();
  });
}

function renderTargets() {
  const current = state.planningTargets[0] || { minimumDailyTarget: 0, regularDailyCapacity: 1, effectiveFrom: state.date };
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Targets de produção</h2></div>
      <form id="targets-form" class="form-grid">
        <label>Target mínimo diário
          <input name="minimumDailyTarget" type="number" min="0" value="${current.minimumDailyTarget}" required>
        </label>
        <label>Target máximo diário — capacidade regular
          <input name="regularDailyCapacity" type="number" min="1" value="${current.regularDailyCapacity}" required>
        </label>
        <label>Data de entrada em vigor
          <input name="effectiveFrom" type="date" value="${state.date}" required>
        </label>
        <button type="submit" ${state.busy.targets ? 'disabled' : ''}>${state.busy.targets ? 'A guardar...' : 'Guardar targets'}</button>
        <p id="targets-message" class="message hidden wide" role="alert"></p>
      </form>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Histórico de alterações</h2></div>
      <div class="table-wrap"><table>
        <thead><tr><th>Entrada em vigor</th><th>Target mínimo</th><th>Target máximo</th><th>Alterado por</th><th>Data</th></tr></thead>
        <tbody>${state.planningTargets.map(target => `<tr>
          <td>${pp.formatDate(target.effectiveFrom)}</td>
          <td>${target.minimumDailyTarget}</td>
          <td>${target.regularDailyCapacity}</td>
          <td>${pp.escapeHtml(target.createdBy)}</td>
          <td>${pp.formatDateTime(target.createdAt)}</td>
        </tr>`).join('')}</tbody>
      </table></div>
    </section>`;
  document.querySelector('#targets-form').addEventListener('submit', saveTargets);
}

async function saveTargets(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = document.querySelector('#targets-message');
  const submit = form.querySelector('button[type="submit"]');
  pp.hideMessage(message);
  const minimumDailyTarget = Number(form.minimumDailyTarget.value);
  const regularDailyCapacity = Number(form.regularDailyCapacity.value);
  if (minimumDailyTarget > regularDailyCapacity) {
    pp.showMessage(message, 'Target mínimo diário não pode ser superior ao target máximo diário.');
    return;
  }
  try {
    state.busy.targets = true;
    submit.disabled = true;
    submit.textContent = 'A guardar...';
    await api.postJson('/api/v1/admin/planning-targets', {
      minimumDailyTarget,
      regularDailyCapacity,
      effectiveFrom: form.effectiveFrom.value
    });
    await loadAdminData();
    state.busy.targets = false;
    renderTargets();
    pp.showMessage(document.querySelector('#targets-message'), 'Targets guardados. O planeamento será atualizado quando clicar em Recalcular.', 'success');
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível guardar os targets.');
  } finally {
    state.busy.targets = false;
    submit.disabled = false;
    submit.textContent = 'Guardar targets';
  }
}

function renderShiftClosure() {
  const plan = state.productionPlan;
  const lines = plan?.lines || [];
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header">
        <h2>Fecho do turno</h2>
        <button id="mark-all-complete" type="button" class="secondary" ${state.busy.markAll ? 'disabled' : ''}>Marcar tudo como concluído</button>
      </div>
      ${plan?.warning?.includes('fecho do turno anterior pendente') ? `<p class="message warning">Aviso de fecho pendente: valide o dia anterior para tornar o plano definitivo.</p>` : ''}
      <form id="closure-form">
        <div class="closure-summary" id="closure-summary"></div>
        <div class="closure-cards">${lines.map(closureCard).join('')}</div>
        <input name="planVersion" type="hidden" value="${plan?.version || 0}">
        <div class="actions" style="margin-top:12px">
          <button id="save-reconciliation" type="button" class="secondary" ${state.busy.closure ? 'disabled' : ''}>${state.busy.closure ? 'A guardar...' : 'Guardar rascunho'}</button>
          <button type="submit" ${state.busy.closure ? 'disabled' : ''}>${state.busy.closure ? 'A fechar...' : 'Validar e fechar turno'}</button>
        </div>
        <p id="closure-message" class="message hidden" role="alert"></p>
      </form>
    </section>`;
  bindClosureForm();
  document.querySelectorAll('[data-closure-line]').forEach(validateClosureRow);
  updateClosureTotals();
}

function closureCard(line) {
  return `<article class="closure-card" data-closure-line>
    <section class="closure-info">
      <input type="hidden" data-line-id value="${line.id}">
      <input type="hidden" data-line-version value="${line.version}">
      ${infoRow('Cliente', line.customerName)}
      ${infoRow('Pedido', line.requestCode || '-')}
      ${infoRow('Total planeado', line.plannedQuantity)}
      ${infoRow('Bipartidas', quantityValue(line.wheelQuantities, 'BIPARTITE'))}
      ${infoRow('Lavadas', quantityValue(line.wheelQuantities, 'WASHED'))}
      ${infoRow('Normais', quantityValue(line.wheelQuantities, 'NORMAL'))}
      ${infoRow('Prazo', pp.formatDateTime(line.deadlineAt))}
      ${infoRow('Estado', line.status)}
    </section>
    <section class="closure-edit">
      <table class="closure-type-table">
        <thead><tr><th>Tipo</th><th>Planeado</th><th>Concluído</th><th>Pendente</th></tr></thead>
        <tbody>${typeClosureRows(line)}</tbody>
        <tfoot><tr><th>Total</th><th data-row-planned>${line.plannedQuantity}</th><th data-row-completed>0</th><th data-row-remaining>0</th></tr></tfoot>
      </table>
      <label>Notas operacionais<input data-notes value="${pp.escapeHtml(line.operationalNotes || '')}"></label>
      <p class="message error hidden" data-line-error></p>
    </section>
  </article>`;
}

function infoRow(label, value) {
  return `<div class="info-row"><span>${pp.escapeHtml(label)}</span><strong>${pp.escapeHtml(value)}</strong></div>`;
}

function bindClosureForm() {
  document.querySelector('#mark-all-complete')?.addEventListener('click', () => {
    document.querySelectorAll('[data-closure-line]').forEach(row => {
      row.querySelectorAll('[data-type-completed]').forEach(input => input.value = input.dataset.typePlanned);
      row.querySelectorAll('[data-type-remaining]').forEach(input => input.value = 0);
      validateClosureRow(row);
    });
    updateClosureTotals();
  });
  document.querySelectorAll('[data-type-completed]').forEach(input => input.addEventListener('input', syncTypeRemaining));
  document.querySelectorAll('[data-type-remaining]').forEach(input => input.addEventListener('input', syncTypeCompleted));
  document.querySelector('#save-reconciliation')?.addEventListener('click', () => submitClosure(false));
  document.querySelector('#closure-form')?.addEventListener('submit', event => {
    event.preventDefault();
    submitClosure(true);
  });
}

function syncTypeRemaining(event) {
  const input = event.target;
  const row = input.closest('[data-closure-line]');
  const target = row.querySelector(`[data-type-remaining][data-type="${input.dataset.type}"]`);
  target.value = Math.max(Number(input.dataset.typePlanned) - Number(input.value || 0), 0);
  validateClosureRow(row);
  updateClosureTotals();
}

function syncTypeCompleted(event) {
  const input = event.target;
  const row = input.closest('[data-closure-line]');
  const target = row.querySelector(`[data-type-completed][data-type="${input.dataset.type}"]`);
  target.value = Math.max(Number(input.dataset.typePlanned) - Number(input.value || 0), 0);
  validateClosureRow(row);
  updateClosureTotals();
}

async function submitClosure(close) {
  const message = document.querySelector('#closure-message');
  pp.hideMessage(message);
  try {
    const invalid = [...document.querySelectorAll('[data-closure-line]')].some(row => !validateClosureRow(row));
    if (invalid) {
      pp.showMessage(message, 'Corrija as quantidades antes de fechar o turno.');
      return;
    }
    state.busy.closure = true;
    document.querySelectorAll('#closure-form button').forEach(button => button.disabled = true);
    const payload = reconciliationPayload();
    state.productionPlan = await api[close ? 'postJson' : 'putJson'](`/api/v1/admin/production-plans/${state.date}/${close ? 'close' : 'reconciliation'}`, payload);
    await loadAdminData();
    renderShiftClosure();
    pp.showMessage(document.querySelector('#closure-message'), close ? 'Turno fechado com sucesso.' : 'Rascunho guardado com sucesso.', 'success');
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível guardar o fecho.');
  } finally {
    state.busy.closure = false;
    document.querySelectorAll('#closure-form button').forEach(button => button.disabled = false);
  }
}

function reconciliationPayload() {
  const form = document.querySelector('#closure-form');
  return {
    planVersion: Number(form.planVersion.value),
    lines: [...document.querySelectorAll('[data-closure-line]')].map(row => {
      const wheelQuantities = ['BIPARTITE', 'WASHED', 'NORMAL'].map(type => ({
        type,
        completedQuantity: Number(row.querySelector(`[data-type-completed][data-type="${type}"]`).value || 0),
        remainingQuantity: Number(row.querySelector(`[data-type-remaining][data-type="${type}"]`).value || 0)
      }));
      return {
        lineId: row.querySelector('[data-line-id]').value,
        version: Number(row.querySelector('[data-line-version]').value),
        completedQuantity: wheelQuantities.reduce((sum, quantity) => sum + quantity.completedQuantity, 0),
        remainingQuantity: wheelQuantities.reduce((sum, quantity) => sum + quantity.remainingQuantity, 0),
        wheelQuantities,
        operationalNotes: row.querySelector('[data-notes]').value
      };
    })
  };
}

function renderDashboard() {
  const d = state.dashboard;
  if (!d) return;
  const plan = d.plan;
  const items = plan?.items || [];
  document.querySelector('#admin-app').innerHTML = `
    <section class="metric-grid">
      ${metric('Objetivo diário', d.dailyTarget)}
      ${metric('Capacidade diária', d.dailyCapacity)}
      ${metric('Jantes planeadas', d.wheelsPlanned)}
      ${metric('Na fábrica', d.wheelsAtFactory)}
      ${metric('Esperadas hoje', d.wheelsExpectedToday)}
      ${metric('Em produção', d.wheelsInProduction)}
      ${metric('Prontas', d.wheelsReady)}
      ${metric('Em risco', d.wheelsAtRisk)}
      ${metric('Excesso', d.capacityOverflow)}
      ${metric('Capacidade livre', d.remainingAvailableCapacity)}
    </section>
    ${capacityAlertBanner()}
    ${plan?.warning ? `<p class="message warning">${pp.escapeHtml(plan.warning)}</p>` : ''}
    <p class="connection-status">Última geração: ${plan ? pp.formatDateTime(plan.generatedAt) + ' · versão ' + plan.versionNumber : 'sem plano gerado'}</p>
    ${filters()}
    <section class="panel">
      <div class="section-header"><h2>Plano de produção por janela de pronto</h2></div>
      <div class="window-grid">${renderPlanByWindow(filterItems(items))}</div>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Lista de produção priorizada</h2></div>
      ${planTable(filterItems(items))}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Chegadas previstas à fábrica</h2></div>
      <div class="list-stack">${d.expectedFactoryArrivals.length ? d.expectedFactoryArrivals.map(arrivalItem).join('') : '<p class="muted">Sem chegadas previstas para hoje.</p>'}</div>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Riscos e exceções</h2></div>
      <div class="list-stack">${d.risksAndExceptions.length ? d.risksAndExceptions.map(text => `<p class="message warning">${pp.escapeHtml(text)}</p>`).join('') : '<p class="muted">Sem riscos assinalados.</p>'}</div>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Carga futura</h2></div>
      <div class="list-stack">${d.futureWorkload.length ? d.futureWorkload.map(arrivalItem).join('') : '<p class="muted">Sem carga futura registada.</p>'}</div>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Capacidade</h2><span class="status">Em construção</span></div>
      <p class="muted">Capacidade está temporariamente em construção e não influencia o planeamento atual.</p>
    </section>
  `;
  bindDashboardActions();
  document.querySelectorAll('.filters select').forEach(input => input.addEventListener('input', () => {
    state.filters.driver = document.querySelector('#filter-driver')?.value || '';
    state.filters.customer = document.querySelector('#filter-customer')?.value || '';
    state.filters.status = document.querySelector('#filter-status')?.value || '';
    state.filters.risk = document.querySelector('#filter-risk')?.value || '';
    state.filters.confidence = document.querySelector('#filter-confidence')?.value || '';
    renderDashboard();
  }));
}

function capacityAlertBanner() {
  return '';
}

function filters() {
  return `
    <section class="panel">
      <div class="filters">
        <select id="filter-driver"><option value="">Todos os motoristas</option>${state.drivers.map(driver => `<option value="${driver.name}" ${state.filters.driver === driver.name ? 'selected' : ''}>${pp.escapeHtml(driver.name)}</option>`).join('')}</select>
        <select id="filter-customer"><option value="">Todos os clientes</option>${state.customers.map(customer => `<option value="${customer.name}" ${state.filters.customer === customer.name ? 'selected' : ''}>${pp.escapeHtml(customer.name)}</option>`).join('')}</select>
        <select id="filter-status"><option value="">Todos os estados</option>${['REGISTERED','ARRIVED_AT_FACTORY','IN_PRODUCTION','READY_FOR_PICKUP','PICKED_UP_FROM_FACTORY','CANCELLED'].map(status => `<option ${state.filters.status === status ? 'selected' : ''}>${status}</option>`).join('')}</select>
        <select id="filter-risk"><option value="">Todos os riscos</option>${['ON_TRACK','AT_RISK','OVERDUE','OVER_CAPACITY','MISSING_INFORMATION'].map(risk => `<option ${state.filters.risk === risk ? 'selected' : ''}>${risk}</option>`).join('')}</select>
        <select id="filter-confidence"><option value="">Todas as disponibilidades</option>${['CONFIRMED','TENTATIVE','WAITING_FOR_ARRIVAL'].map(confidence => `<option ${state.filters.confidence === confidence ? 'selected' : ''}>${confidence}</option>`).join('')}</select>
      </div>
    </section>
  `;
}

function filterItems(items) {
  const driver = state.filters.driver;
  const customer = state.filters.customer;
  const status = state.filters.status;
  const risk = state.filters.risk;
  const confidence = state.filters.confidence;
  return items.filter(item =>
    (!driver || item.driverName === driver)
    && (!customer || item.customerName === customer)
    && (!status || requestById(item.requestId)?.lifecycleStatus === status)
    && (!risk || item.riskClassification === risk)
    && (!confidence || item.availabilityClassification === confidence)
  );
}

function renderPlanByWindow(items) {
  if (!items.length) return '<p class="muted">Sem itens para os filtros selecionados.</p>';
  const grouped = new Map();
  for (const item of items) {
    const key = item.assignedWindowLabel || 'Sem janela';
    grouped.set(key, [...(grouped.get(key) || []), item]);
  }
  return [...grouped.entries()].map(([label, group]) => `
    <article class="window-column">
      <h3>${pp.escapeHtml(label)}</h3>
      <div class="list-stack">${group.map(item => `
        <div class="list-item">
          <strong>${pp.escapeHtml(item.customerName)}</strong>
          <span>Total: ${item.quantity} jantes · ${wheelSummary(item.wheelQuantities)}</span>
          <span>${pp.escapeHtml(item.driverName)}</span>
          <span>${pp.badge(item.availabilityClassification)} ${pp.badge(item.riskClassification)}</span>
        </div>`).join('')}
      </div>
    </article>
  `).join('');
}

function planTable(items) {
  if (!items.length) return '<p class="muted">Sem itens para os filtros selecionados.</p>';
  return `<div class="table-wrap"><table>
    <thead><tr><th>Prioridade</th><th>Cliente</th><th>Motorista</th><th>Total</th><th>Tipos de jantes</th><th>Chegada fábrica</th><th>Levantamento fábrica</th><th>Estado</th><th>Disponibilidade</th><th>Risco</th><th>Explicação</th><th>Ações</th></tr></thead>
    <tbody>${items.map(item => {
      const request = requestById(item.requestId);
      return `<tr>
        <td>${item.priorityScore}</td>
        <td>${pp.escapeHtml(item.customerName)}</td>
        <td>${pp.escapeHtml(item.driverName)}</td>
        <td>${item.quantity}</td>
        <td>${wheelSummary(item.wheelQuantities)}</td>
        <td>${pp.formatDateTime(item.availabilityAt)}</td>
        <td>${pp.formatDateTime(item.requiredReadyAt)}</td>
        <td>${request ? pp.badge(request.lifecycleStatus) : '-'}</td>
        <td>${pp.badge(item.availabilityClassification)}</td>
        <td>${pp.badge(item.riskClassification)}</td>
        <td>${pp.escapeHtml(item.priorityExplanation)}</td>
        <td><div class="actions">
          ${request ? `<button type="button" class="secondary" data-arrival="${request.id}">Chegada</button>
          <button type="button" class="secondary" data-status="${request.id}">Estado</button>
          <button type="button" class="secondary" data-priority="${request.id}">Prioridade</button>` : ''}
        </div></td>
      </tr>`;
    }).join('')}</tbody>
  </table></div>`;
}

function bindDashboardActions() {
  document.querySelectorAll('[data-arrival]').forEach(button => button.addEventListener('click', () => confirmArrival(button.dataset.arrival)));
  document.querySelectorAll('[data-status]').forEach(button => button.addEventListener('click', () => updateStatus(button.dataset.status)));
  document.querySelectorAll('[data-priority]').forEach(button => button.addEventListener('click', () => updatePriority(button.dataset.priority)));
  document.querySelectorAll('[data-alert-ack]').forEach(button => button.addEventListener('click', () => acknowledgeAlert(button.dataset.alertAck)));
}

async function acknowledgeAlert(id) {
  await api.patchJson(`/api/v1/admin/capacity-alerts/${id}/acknowledge`, {});
  await loadAdminData();
  render();
}

async function regeneratePlan() {
  state.busy.regenerate = true;
  const button = document.querySelector('#regenerate-button');
  if (button) {
    button.disabled = true;
    button.textContent = 'A gerar...';
  }
  try {
    state.productionPlan = await api.postJson(`/api/v1/admin/production-plans/${state.date}/recalculate`, {});
    await loadAdminData();
    render();
  } catch (error) {
    alert(error.message || 'Não foi possível gerar o plano.');
  } finally {
    state.busy.regenerate = false;
    if (button) {
      button.disabled = false;
      button.textContent = 'Gerar plano';
    }
  }
}

async function confirmArrival(id) {
  const request = requestById(id);
  if (!request) return;
  const quantity = Number(prompt('Quantidade recebida na fábrica:', request.actualReceivedWheelQuantity || request.totalQuantity || request.expectedWheelQuantity));
  if (!quantity) return;
  const acknowledge = quantity !== (request.totalQuantity || request.expectedWheelQuantity) ? confirm('A quantidade é diferente do esperado. Pretende reconhecer a diferença?') : false;
  await api.postJson(`/api/v1/admin/requests/${id}/arrival`, {
    actualFactoryArrivalAt: new Date().toISOString(),
    actualReceivedWheelQuantity: quantity,
    acknowledgeDiscrepancy: acknowledge,
    reason: 'Confirmado no dashboard',
    version: request.version
  });
  await loadAdminData();
  render();
}

async function updateStatus(id) {
  const request = requestById(id);
  if (!request) return;
  const status = prompt('Novo estado: ARRIVED_AT_FACTORY, IN_PRODUCTION, READY_FOR_PICKUP, PICKED_UP_FROM_FACTORY, CANCELLED', request.lifecycleStatus);
  if (!status) return;
  await api.postJson(`/api/v1/admin/requests/${id}/status`, {
    status,
    actualPickupFromFactoryAt: status === 'PICKED_UP_FROM_FACTORY' ? new Date().toISOString() : null,
    reason: 'Atualizado no dashboard',
    version: request.version
  });
  await loadAdminData();
  render();
}

async function updatePriority(id) {
  const request = requestById(id);
  if (!request) return;
  const value = prompt('Prioridade manual vazia ou número maior que zero:', request.manualPriority || '');
  if (value === null) return;
  const locked = confirm('Bloquear esta decisão de planeamento?');
  await api.postJson(`/api/v1/admin/requests/${id}/priority`, {
    manualPriority: value.trim() ? Number(value) : null,
    planningLocked: locked,
    reason: 'Atualizado no dashboard',
    version: request.version
  });
  await loadAdminData();
  render();
}

function renderRequests() {
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Pedidos registados</h2></div>
      <div class="table-wrap"><table class="requests-table">
        <thead><tr><th>Pedido</th><th>Cliente</th><th>Motorista</th><th>Tipos de jantes</th><th>Deixar na fábrica</th><th>Levantar na fábrica</th><th>Estado</th><th>Diferença</th><th>Ações</th></tr></thead>
        <tbody>${state.requests.map(request => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(request.requestCode || '-')}</span></td>
          <td>${pp.escapeHtml(request.customerNameSnapshot)}</td>
          <td>${pp.escapeHtml(request.driverName)}</td>
          <td>${wheelSummaryVertical(request.wheelQuantities)}</td>
          <td>${factoryWindow(request.expectedFactoryDropOffWindowStart, request.expectedFactoryDropOffWindowEnd)}</td>
          <td>${factoryWindow(request.requestedFactoryPickupWindowStart, request.requestedFactoryPickupWindowEnd)}</td>
          <td>${pp.badge(request.lifecycleStatus)}</td>
          <td>${request.quantityDiscrepancy ? pp.badge('AT_RISK') : '-'}</td>
          <td><button type="button" class="secondary" data-status="${request.id}">Estado</button></td>
        </tr>`).join('')}</tbody>
      </table></div>
    </section>`;
  document.querySelectorAll('[data-status]').forEach(button => button.addEventListener('click', () => updateStatus(button.dataset.status)));
}

function renderSettings() {
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Capacidade</h2><span class="status">Em construção</span></div>
      <p class="message warning">A configuração de capacidade está temporariamente desativada. Nesta fase, o planeamento usa apenas os targets mínimo e máximo.</p>
      <p class="muted">A funcionalidade foi preservada para uso futuro, mas não permite navegação nem edição operacional neste momento.</p>
    </section>`;
}

function windowRow(window) {
  return `<div class="list-item" data-window-row>
    <div class="form-grid">
      <label>Etiqueta<input data-window-label value="${pp.escapeHtml(window.label)}" required></label>
      <label>Hora limite<input data-window-cutoff type="time" value="${window.cutoffTime}" required></label>
      <label>Ordem<input data-window-order type="number" min="1" value="${window.sortOrder || 1}" required></label>
    </div>
  </div>`;
}

async function saveSettings(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = document.querySelector('#settings-message');
  pp.hideMessage(message);
  const timeWindows = [...document.querySelectorAll('[data-window-row]')].map(row => ({
    label: row.querySelector('[data-window-label]').value.trim(),
    cutoffTime: row.querySelector('[data-window-cutoff]').value,
    sortOrder: Number(row.querySelector('[data-window-order]').value)
  }));
  try {
    await api.putJson(`/api/v1/admin/settings/daily?date=${state.date}`, {
      dailyCapacity: Number(form.dailyCapacity.value),
      dailyTarget: Number(form.dailyTarget.value),
      fallbackMinutesPerWheel: Number(form.fallbackMinutesPerWheel.value),
      timeWindows,
      version: Number(form.version.value)
    });
    await loadAdminData();
    renderSettings();
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível guardar.');
  }
}

function renderDrivers() {
  const query = (state.driverIdentityQuery || '').toLowerCase();
  const identities = state.messagingIdentities.filter(identity => {
    const haystack = [
      identity.driverName,
      identity.externalUsername,
      identity.platformFirstName,
      identity.platformLastName,
      identity.externalUserId,
      identity.channel,
      identity.onboardingStatus
    ].filter(Boolean).join(' ').toLowerCase();
    return !query || haystack.includes(query);
  });
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Motoristas e ligações</h2></div>
      <form id="driver-form" class="form-grid">
        <label>Código externo<input name="externalId"></label>
        <label>Nome<input name="name" required></label>
        <label>Utilizador<input name="username"></label>
        <label>Palavra-passe<input name="password" type="password"></label>
        <button type="submit">Criar motorista</button>
      </form>
      <div class="table-wrap" style="margin-top:12px"><table><thead><tr><th>Código</th><th>Nome</th><th>ID RucoFi</th><th>Estado</th><th>Ações</th></tr></thead><tbody>
        ${state.drivers.map(driver => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(driver.driverCode || '-')}</span></td>
          <td>${pp.escapeHtml(driver.name)}</td>
          <td>${pp.escapeHtml(driver.externalId || '-')}</td>
          <td>${driver.active ? 'Ativo' : 'Inativo'}</td>
          <td><button type="button" class="secondary" data-driver-rename="${driver.id}">Corrigir nome</button></td>
        </tr>`).join('')}
      </tbody></table></div>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Identidades Telegram e canais de comunicação</h2></div>
      <label>Pesquisar por nome, username ou identificador
        <input id="identity-search" value="${pp.escapeHtml(state.driverIdentityQuery || '')}">
      </label>
      <div class="table-wrap" style="margin-top:12px"><table>
        <thead><tr><th>Código</th><th>Nome</th><th>Canal</th><th>Username</th><th>Contacto</th><th>Estado</th><th>Última atividade</th><th>Ações</th></tr></thead>
        <tbody>${identities.map(identity => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(identity.driverCode || '-')}</span></td>
          <td>${pp.escapeHtml(identity.driverName || 'Pendente')}</td>
          <td>${pp.escapeHtml(identity.channel)}</td>
          <td>${pp.escapeHtml(identity.externalUsername || '-')}</td>
          <td>${pp.escapeHtml(identity.maskedPhoneNumber || '-')}</td>
          <td>${pp.escapeHtml(identity.onboardingStatus)}</td>
          <td>${pp.formatDateTime(identity.lastSeenAt)}</td>
          <td>
            ${identity.blockedAt
              ? `<button type="button" class="secondary" data-identity-reactivate="${identity.id}">Reativar</button>`
              : `<button type="button" class="secondary" data-identity-block="${identity.id}">Bloquear</button>`}
            <button type="button" class="secondary" data-identity-link="${identity.id}">Associar</button>
          </td>
        </tr>`).join('')}</tbody>
      </table></div>
    </section>`;
  document.querySelector('#driver-form').addEventListener('submit', createDriver);
  document.querySelector('#identity-search')?.addEventListener('input', event => {
    state.driverIdentityQuery = event.target.value;
    renderDrivers();
  });
  document.querySelectorAll('[data-driver-rename]').forEach(button => button.addEventListener('click', () => renameDriver(button.dataset.driverRename)));
  document.querySelectorAll('[data-identity-block]').forEach(button => button.addEventListener('click', () => blockIdentity(button.dataset.identityBlock)));
  document.querySelectorAll('[data-identity-reactivate]').forEach(button => button.addEventListener('click', () => reactivateIdentity(button.dataset.identityReactivate)));
  document.querySelectorAll('[data-identity-link]').forEach(button => button.addEventListener('click', () => linkIdentity(button.dataset.identityLink)));
}

async function createDriver(event) {
  event.preventDefault();
  const form = event.currentTarget;
  await api.postJson('/api/v1/admin/drivers', {
    externalId: form.externalId.value.trim(),
    name: form.name.value.trim(),
    active: true,
    username: form.username.value.trim(),
    password: form.password.value
  });
  await loadAdminData();
  renderDrivers();
}

async function renameDriver(id) {
  const driver = state.drivers.find(item => item.id === id);
  const name = prompt('Novo nome do motorista:', driver?.name || '');
  if (!name || !name.trim()) return;
  await api.patchJson(`/api/v1/admin/drivers/${id}`, {
    externalId: driver.externalId || '',
    name: name.trim(),
    active: driver.active,
    version: driver.version
  });
  await loadAdminData();
  renderDrivers();
}

async function blockIdentity(id) {
  if (!confirm('Bloquear esta ligação Telegram?')) return;
  await api.postJson(`/api/v1/admin/messaging-identities/${id}/block`, {});
  await loadAdminData();
  renderDrivers();
}

async function reactivateIdentity(id) {
  await api.postJson(`/api/v1/admin/messaging-identities/${id}/reactivate`, {});
  await loadAdminData();
  renderDrivers();
}

async function linkIdentity(id) {
  const driverName = prompt('Nome exato do motorista existente a associar:');
  if (!driverName || !driverName.trim()) return;
  const driver = state.drivers.find(item => item.name.toLowerCase() === driverName.trim().toLowerCase());
  if (!driver) {
    alert('Motorista não encontrado.');
    return;
  }
  if (!confirm(`Associar esta identidade a ${driver.name}?`)) return;
  await api.postJson(`/api/v1/admin/messaging-identities/${id}/link`, {
    driverId: driver.id,
    reason: 'Associação manual no painel administrativo'
  });
  await loadAdminData();
  renderDrivers();
}

function renderCustomers() {
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Clientes pendentes</h2></div>
      ${pendingCustomersTable()}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Clientes locais</h2></div>
      <form id="customer-form" class="form-grid">
        <label>Nome<input name="name" required></label>
        <label>NIF/VAT<input name="taxIdentifier"></label>
        <label>País
          <select name="countryCode">
            <option value="PT">Portugal</option>
            <option value="ES">Espanha</option>
            <option value="FR">França</option>
            <option value="LU">Luxemburgo</option>
          </select>
        </label>
        <label>Localidade<input name="locality"></label>
        <label>ID RucoFi opcional<input name="rucofiId"></label>
        <button type="submit" ${state.busy.createCustomer ? 'disabled' : ''}>${state.busy.createCustomer ? 'A criar...' : 'Criar cliente'}</button>
        <p id="customer-message" class="message hidden wide" role="alert"></p>
      </form>
      <div class="table-wrap" style="margin-top:12px"><table><thead><tr><th>Código</th><th>Nome</th><th>NIF/VAT</th><th>País</th><th>Localidade</th><th>ID RucoFi</th><th>Estado</th><th>Ações</th></tr></thead><tbody>
        ${state.customers.map(customer => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(customer.customerCode || '-')}</span></td>
          <td>${pp.escapeHtml(customer.name)}</td>
          <td>${pp.escapeHtml(customer.taxIdentifier || '-')}</td>
          <td>${pp.escapeHtml(countryName(customer.countryCode))}</td>
          <td>${pp.escapeHtml(customer.locality || '-')}</td>
          <td>${pp.escapeHtml(rucofiId(customer) || '-')}</td>
          <td>${customer.active ? 'Ativo' : 'Inativo'}</td>
          <td><button type="button" class="secondary" disabled>Editar</button></td>
        </tr>`).join('')}
      </tbody></table></div>
    </section>`;
  document.querySelector('#customer-form').addEventListener('submit', createCustomer);
  document.querySelectorAll('[data-registration-link]').forEach(button => button.addEventListener('click', () => linkCustomerRegistration(button.dataset.registrationLink)));
  document.querySelectorAll('[data-registration-reject]').forEach(button => button.addEventListener('click', () => rejectCustomerRegistration(button.dataset.registrationReject)));
}

function pendingCustomersTable() {
  if (!state.customerRegistrationRequests.length) return '<p class="muted">Não existem clientes pendentes de validação.</p>';
  return `<div class="table-wrap"><table>
    <thead><tr><th>Nome proposto</th><th>Motorista</th><th>Dados fornecidos</th><th>Estado</th><th>Pedido em</th><th>Ações</th></tr></thead>
    <tbody>${state.customerRegistrationRequests.map(item => `<tr>
      <td>${pp.escapeHtml(item.proposedName)}</td>
      <td>${pp.escapeHtml(item.requestedByDriverName || '-')}</td>
      <td>
        Nº RucoPlan: ${pp.escapeHtml(item.reservedCustomerNumber || 'Não informado')}<br>
        NIF/VAT: ${pp.escapeHtml(item.maskedTaxIdentifier || 'Não informado')}<br>
        País: ${pp.escapeHtml(item.countryCode || 'Não informado')}<br>
        Localidade: ${pp.escapeHtml(item.locality || 'Não informado')}
      </td>
      <td>${pp.badge(item.status)}</td>
      <td>${pp.formatDateTime(item.createdAt)}</td>
      <td>
        <button type="button" class="secondary" data-registration-link="${item.id}">Associar a existente</button>
        <button type="button" class="secondary" data-registration-reject="${item.id}">Rejeitar</button>
      </td>
    </tr>`).join('')}</tbody>
  </table></div>`;
}

async function linkCustomerRegistration(id) {
  const query = prompt('Nome exato do cliente existente a associar:');
  if (!query || !query.trim()) return;
  const customer = state.customers.find(item => item.name.toLowerCase() === query.trim().toLowerCase());
  if (!customer) {
    alert('Cliente não encontrado.');
    return;
  }
  if (!confirm(`Associar este registo pendente a ${customer.name}?`)) return;
  await api.postJson(`/api/v1/admin/customer-registration-requests/${id}/link`, {
    customerId: customer.id,
    notes: 'Associação manual no painel administrativo'
  });
  await loadAdminData();
  renderCustomers();
}

async function rejectCustomerRegistration(id) {
  const notes = prompt('Motivo da rejeição:') || '';
  if (!confirm('Rejeitar este cliente pendente?')) return;
  await api.postJson(`/api/v1/admin/customer-registration-requests/${id}/reject`, { notes });
  await loadAdminData();
  renderCustomers();
}

async function createCustomer(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = document.querySelector('#customer-message');
  const submit = form.querySelector('button[type="submit"]');
  pp.hideMessage(message);
  state.busy.createCustomer = true;
  submit.disabled = true;
  submit.textContent = 'A criar...';
  try {
    const created = await api.postJson('/api/v1/admin/customers', {
      name: form.name.value.trim(),
      taxIdentifier: form.taxIdentifier.value.trim(),
      countryCode: form.countryCode.value.trim(),
      locality: form.locality.value.trim(),
      externalSystem: form.rucofiId.value.trim() ? 'RUCOFI' : '',
      externalCustomerId: form.rucofiId.value.trim(),
      active: true
    });
    await loadAdminData();
    state.busy.createCustomer = false;
    renderCustomers();
    pp.showMessage(document.querySelector('#customer-message'), `Cliente criado com o código ${created.customerCode}.`, 'success');
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível criar o cliente.');
  } finally {
    state.busy.createCustomer = false;
    submit.disabled = false;
    submit.textContent = 'Criar cliente';
  }
}

function renderAudit() {
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>Auditoria</h2></div>
      <div class="table-wrap"><table><thead><tr><th>Data</th><th>Evento</th><th>Ator</th><th>Detalhe</th></tr></thead><tbody>
        ${state.audit.map(event => `<tr><td>${pp.formatDateTime(event.createdAt)}</td><td>${pp.escapeHtml(event.eventType)}</td><td>${pp.escapeHtml(event.actor)}</td><td>${pp.escapeHtml(event.detail)}</td></tr>`).join('')}
      </tbody></table></div>
    </section>`;
}

function connectStream() {
  const current = api.session();
  if (!('EventSource' in window) || !current?.token) {
    setConnectionStatus('Sem ligação em tempo real');
    return;
  }
  if (state.stream) state.stream.close();
  state.stream = new EventSource(`/api/v1/admin/dashboard/stream?access_token=${encodeURIComponent(current.token)}`);
  state.stream.addEventListener('open', () => setConnectionStatus('Ligado em tempo real'));
  state.stream.addEventListener('dashboard-connected', () => setConnectionStatus('Ligado em tempo real'));
  state.stream.addEventListener('production-plan-updated', async event => {
    try {
      const payload = JSON.parse(event.data);
      if (payload.date === state.date) {
        await loadAdminData();
        render();
      }
    } catch (_) {
      setConnectionStatus('Atualização recebida com erro');
    }
  });
  state.stream.addEventListener('error', () => setConnectionStatus('A tentar reconectar...'));
  window.addEventListener('beforeunload', () => state.stream?.close(), { once: true });
}

function startFallbackPolling() {
  window.clearInterval(state.fallbackTimer);
  state.fallbackTimer = window.setInterval(async () => {
    await loadAdminData();
    render();
  }, 30000);
}

function setConnectionStatus(text) {
  document.querySelector('#connection-status').textContent = text;
}

function metric(label, value) {
  return `<div class="metric"><span>${pp.escapeHtml(label)}</span><strong>${pp.escapeHtml(value)}</strong></div>`;
}

function arrivalItem(request) {
  return `<article class="list-item">
    <div class="row-between"><strong>${pp.escapeHtml(request.customerNameSnapshot)}</strong>${pp.badge(request.lifecycleStatus)}</div>
    <div>Total: ${request.totalQuantity || request.expectedWheelQuantity} jantes · ${wheelSummary(request.wheelQuantities)} · ${pp.escapeHtml(request.driverName)}</div>
    <div class="muted">Deixar na fábrica: ${pp.formatDateTime(request.expectedFactoryDropOffWindowStart)} a ${pp.formatDateTime(request.expectedFactoryDropOffWindowEnd)}</div>
    <div class="muted">Levantar na fábrica: ${pp.formatDateTime(request.requestedFactoryPickupWindowStart)} a ${pp.formatDateTime(request.requestedFactoryPickupWindowEnd)}</div>
  </article>`;
}

function requestById(id) {
  return state.requests.find(request => request.id === id);
}

function wheelSummary(quantities = []) {
  const bipartite = quantityValue(quantities, 'BIPARTITE');
  const washed = quantityValue(quantities, 'WASHED');
  const normal = quantityValue(quantities, 'NORMAL');
  return `${bipartite} bipartidas · ${washed} lavadas · ${normal} normais`;
}

function wheelSummaryVertical(quantities = []) {
  return `<div class="wheel-stack">
    <span>${quantityValue(quantities, 'BIPARTITE')} bipartidas</span>
    <span>${quantityValue(quantities, 'WASHED')} lavadas</span>
    <span>${quantityValue(quantities, 'NORMAL')} normais</span>
  </div>`;
}

function quantityValue(quantities, type) {
  const found = quantities?.find(quantity => quantity.type === type);
  return found?.quantity ?? found?.plannedQuantity ?? 0;
}

function typeClosureRows(line) {
  return ['BIPARTITE', 'WASHED', 'NORMAL'].map(type => {
    const quantity = line.wheelQuantities?.find(item => item.type === type) || {};
    const planned = quantity.plannedQuantity || 0;
    const completed = quantity.completedQuantity ?? 0;
    const remaining = quantity.remainingQuantity ?? planned;
    const label = type === 'BIPARTITE' ? 'Bipartidas' : type === 'WASHED' ? 'Lavadas' : 'Normais';
    return `<tr>
      <td>${label}</td>
      <td data-type-planned-label="${type}">${planned}</td>
      <td><input data-type-completed data-type="${type}" data-type-planned="${planned}" type="number" min="0" max="${planned}" step="1" value="${completed}"></td>
      <td><input data-type-remaining data-type="${type}" data-type-planned="${planned}" type="number" min="0" max="${planned}" step="1" value="${remaining}"></td>
    </tr>`;
  }).join('');
}

function validateClosureRow(row) {
  const error = row.querySelector('[data-line-error]');
  let message = '';
  let totalCompleted = 0;
  let totalRemaining = 0;
  for (const type of ['BIPARTITE', 'WASHED', 'NORMAL']) {
    const completedInput = row.querySelector(`[data-type-completed][data-type="${type}"]`);
    const remainingInput = row.querySelector(`[data-type-remaining][data-type="${type}"]`);
    const planned = Number(completedInput.dataset.typePlanned);
    const completed = Number(completedInput.value);
    const remaining = Number(remainingInput.value);
    if (!Number.isInteger(completed) || !Number.isInteger(remaining) || completedInput.value === '' || remainingInput.value === '') {
      message = 'Use apenas números inteiros em todos os campos.';
      break;
    }
    if (completed < 0 || remaining < 0 || completed > planned || remaining > planned) {
      message = 'As quantidades não podem ser negativas nem superiores ao planeado.';
      break;
    }
    if (completed + remaining !== planned) {
      message = 'Por tipo, planeado tem de ser igual a concluído mais pendente.';
      break;
    }
    totalCompleted += completed;
    totalRemaining += remaining;
  }
  row.querySelector('[data-row-completed]').textContent = totalCompleted;
  row.querySelector('[data-row-remaining]').textContent = totalRemaining;
  if (message) {
    error.textContent = message;
    error.classList.remove('hidden');
    return false;
  }
  error.classList.add('hidden');
  return true;
}

function updateClosureTotals() {
  let planned = 0;
  let completed = 0;
  let remaining = 0;
  document.querySelectorAll('[data-closure-line]').forEach(row => {
    planned += Number(row.querySelector('[data-row-planned]')?.textContent || 0);
    completed += Number(row.querySelector('[data-row-completed]')?.textContent || 0);
    remaining += Number(row.querySelector('[data-row-remaining]')?.textContent || 0);
  });
  const target = document.querySelector('#closure-summary');
  if (target) {
    target.innerHTML = `
      ${metric('Total planeado', planned)}
      ${metric('Total concluído', completed)}
      ${metric('Total pendente', remaining)}
    `;
  }
}

function factoryWindow(start, end) {
  if (!start || !end) return '-';
  const startDate = new Date(start);
  const endDate = new Date(end);
  const date = new Intl.DateTimeFormat('pt-PT', {
    timeZone: 'Europe/Lisbon',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).format(startDate);
  const time = new Intl.DateTimeFormat('pt-PT', {
    timeZone: 'Europe/Lisbon',
    hour: '2-digit',
    minute: '2-digit'
  });
  return `<div class="date-cell"><strong>${date}</strong><span>${time.format(startDate)}–${time.format(endDate)}</span></div>`;
}

function countryName(code) {
  return ({ PT: 'Portugal', ES: 'Espanha', FR: 'França', LU: 'Luxemburgo' })[code] || code || '-';
}

function rucofiId(customer) {
  return customer.externalSystem === 'RUCOFI' ? customer.externalCustomerId : '';
}

function targetsOutdated(plan) {
  if (!plan || !state.planningTargets.length) return false;
  const applicable = state.planningTargets
    .filter(target => target.effectiveFrom <= plan.planningDate)
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0];
  return !!applicable && (applicable.minimumDailyTarget !== plan.minimumDailyTarget
    || applicable.regularDailyCapacity !== plan.regularDailyCapacity);
}
