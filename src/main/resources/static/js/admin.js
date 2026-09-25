const ADMIN_VIEW_REDIRECTS = {
  diagnostics: 'settings/diagnostics',
  audit: 'settings/audit',
  capacity: 'settings/capacity',
  settings: 'settings/diagnostics'
};

const state = {
  date: pp.tomorrowString(),
  view: normalizedViewFromLocation(),
  dashboard: null,
  productionPlan: null,
  productionPlans: [],
  planningTargets: [],
  diagnostics: null,
  diagnosticsError: null,
  loadError: null,
  planningLoaded: false,
  noPlanningData: false,
  targetsError: null,
  settings: null,
  drivers: [],
  messagingIdentities: [],
  customerRegistrationRequests: [],
  customers: [],
  requests: [],
  factoryArrivals: [],
  audit: [],
  capacityAlerts: [],
  busy: {},
  operationNotice: null,
  pageNotice: null,
  stream: null,
  fallbackTimer: null,
  realtime: {
    status: 'CONNECTING',
    attempts: 0,
    retryTimer: null,
    manuallyStopped: false,
    lastConnectedAt: null,
    lastEventAt: null,
    lastFailureAt: null,
    nextRetryAt: null,
    errorCode: null,
    correlationId: null,
    heartbeatStatus: 'UNKNOWN'
  },
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
    applyViewFromLocation();
    syncNavigation();
    render();
  });
  document.querySelectorAll('.nav a').forEach(link => {
    link.addEventListener('click', () => {
      document.querySelectorAll('.nav a').forEach(item => item.classList.toggle('active', item === link));
    });
  });
  window.addEventListener('beforeunload', cleanupAdminConnections, { once: true });
  document.addEventListener('visibilitychange', async () => {
    if (document.visibilityState === 'visible') {
      await refreshAdminData();
    }
  });
  connectStream();
  startFallbackPolling();
  applyViewFromLocation();
  syncNavigation();
  await loadAdminData();
  render();
});

function defaultAdminView() {
  return location.pathname.includes('/admin/system-diagnostics/realtime')
    ? 'settings/diagnostics'
    : 'dashboard';
}

function rawViewFromLocation() {
  return location.hash.replace('#', '') || defaultAdminView();
}

function normalizeAdminView(view) {
  return ADMIN_VIEW_REDIRECTS[view] || view || defaultAdminView();
}

function normalizedViewFromLocation() {
  return normalizeAdminView(rawViewFromLocation());
}

function applyViewFromLocation() {
  const raw = rawViewFromLocation();
  const normalized = normalizeAdminView(raw);
  state.view = normalized;
  if (raw !== normalized) {
    history.replaceState(null, '', `${location.pathname}${location.search}#${normalized}`);
  }
}

async function loadAdminData() {
  try {
    state.diagnostics = await api.get('/api/v1/admin/system-diagnostics');
    state.diagnosticsError = null;
  } catch (error) {
    state.diagnostics = null;
    state.diagnosticsError = normalizeError(error);
  }
  const planningOk = await loadPlanningData();
  await loadOptionalAdminData();
  return planningOk;
}

async function loadPlanningData() {
  const from = pp.addDays(state.date, -2);
  const to = pp.addDays(state.date, 7);
  let planningError = null;
  let loadedAnyPlanningData = false;

  try {
    state.productionPlan = await api.get(`/api/v1/admin/production-plans/${state.date}`);
    loadedAnyPlanningData = true;
  } catch (error) {
    planningError = planningError || normalizeError(error);
    if (state.productionPlan?.planningDate !== state.date) {
      state.productionPlan = null;
    }
  }

  try {
    state.productionPlans = await api.get(`/api/v1/admin/production-plans?from=${from}&to=${to}`) || [];
    loadedAnyPlanningData = true;
  } catch (error) {
    planningError = planningError || normalizeError(error);
  }

  try {
    state.planningTargets = await api.get('/api/v1/admin/planning-targets') || [];
    state.targetsError = null;
  } catch (error) {
    state.targetsError = normalizeError(error);
  }

  state.loadError = planningError;
  state.planningLoaded = loadedAnyPlanningData || !!state.productionPlan || state.productionPlans.length > 0;
  updatePlanningDataState();
  return !planningError;
}

async function loadOptionalAdminData() {
  const optional = await Promise.allSettled([
    api.get(`/api/v1/admin/dashboard?date=${state.date}`),
    api.get(`/api/v1/admin/settings/daily?date=${state.date}`),
    api.get('/api/v1/admin/drivers'),
    api.get('/api/v1/admin/messaging-identities'),
    api.get('/api/v1/admin/customer-registration-requests'),
    api.get('/api/v1/admin/customers'),
    api.get('/api/v1/admin/requests?size=200'),
    api.get('/api/v1/admin/factory-arrivals?status=COMMUNICATED'),
    api.get('/api/v1/admin/audit?size=50')
  ]);
  const value = index => optional[index].status === 'fulfilled' ? optional[index].value : null;
  state.dashboard = value(0) ?? state.dashboard;
  state.settings = value(1) ?? state.settings;
  state.drivers = value(2) || state.drivers;
  state.messagingIdentities = value(3) || state.messagingIdentities;
  state.customerRegistrationRequests = value(4) || state.customerRegistrationRequests;
  state.customers = value(5) || state.customers;
  state.requests = value(6)?.content || state.requests;
  state.factoryArrivals = value(7) || state.factoryArrivals;
  state.audit = value(8)?.content || state.audit;
  state.capacityAlerts = [];
}

function render() {
  if (state.view === 'planning') renderProductionPlanning();
  else if (state.view === 'closure') renderShiftClosure();
  else if (state.view === 'targets') renderTargets();
  else if (state.view === 'requests') renderRequests();
  else if (state.view === 'factory-arrivals') renderFactoryArrivals();
  else if (state.view === 'drivers') renderDrivers();
  else if (state.view === 'customers') renderCustomers();
  else if (state.view.startsWith('settings/')) renderSettingsSection();
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
          <button id="planning-refresh" class="secondary" type="button" ${state.busy.refresh ? 'disabled' : ''}>${state.busy.refresh ? 'A atualizar...' : 'Atualizar'}</button>
          <button id="planning-recalculate" type="button" ${state.busy.recalculate ? 'disabled' : ''}>${state.busy.recalculate ? 'A recalcular...' : 'Recalcular'}</button>
        </div>
      </div>
      ${planningSystemNotices()}
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
    ${metric('Target mínimo', targetMetricValue(planMinimumTarget(plan), plan))}
    ${metric('Target máximo', targetMetricValue(planMaximumTarget(plan), plan))}
    ${metric('Trabalho transportado', plan.carriedOverQuantity)}
    ${metric('Antecipado de dias futuros', plan.advancedQuantity)}
    ${metric('Quantidade em risco', plan.atRiskQuantity)}
    ${metric('Diferença', plan.differenceToMinimum)}
    ${metric('Última atualização', pp.formatDateTime(plan.generatedAt))}
  </section>
  ${plan.warning ? `<p class="message ${isOvertimeRequired(plan) ? 'error' : 'warning'}">${pp.escapeHtml(plan.warning)}</p>` : ''}`;
}

function overtimeMetric(plan) {
  const required = isOvertimeRequired(plan);
  const label = required ? 'Sim' : 'Não';
  const detail = required ? `${planExcessQuantity(plan)} jantes acima do target máximo` : 'Plano dentro do target máximo';
  return `<div class="metric overtime ${required ? 'yes' : 'no'}">
    <span>Horas extra necessárias</span>
    <strong>${label}</strong>
    <small>${pp.escapeHtml(detail)}</small>
  </div>`;
}

function planMinimumTarget(plan) {
  return plan?.minimumDailyTarget ?? plan?.minimumTargetSnapshot ?? plan?.targetUsed ?? null;
}

function planMaximumTarget(plan) {
  return plan?.regularDailyCapacity ?? plan?.maximumTargetSnapshot ?? null;
}

function planTotalPlanned(plan, dashboard) {
  return plan?.totalPlanned ?? dashboard?.wheelsPlanned ?? 0;
}

function planTotalCompleted(plan) {
  return plan?.totalCompleted ?? 0;
}

function planCarriedOver(plan) {
  return plan?.carriedOverQuantity ?? 0;
}

function planExcessQuantity(plan) {
  if (!plan) return 0;
  if (typeof plan.overtimeQuantity === 'number') return plan.overtimeQuantity;
  if (typeof plan.totalOverCapacity === 'number') return plan.totalOverCapacity;
  const maximum = planMaximumTarget(plan);
  return maximum == null ? 0 : Math.max(0, planTotalPlanned(plan) - maximum);
}

function isOvertimeRequired(plan) {
  if (!plan) return false;
  if (typeof plan.overtimeRequired === 'boolean') return plan.overtimeRequired;
  return planExcessQuantity(plan) > 0;
}

function targetMetricValue(value, plan) {
  if (isNonProductionDay(plan)) return 'Sem produção planeada';
  return value == null ? '-' : `${value} jantes`;
}

function isNonProductionDay(plan) {
  return !!plan
    && planMinimumTarget(plan) === 0
    && planMaximumTarget(plan) === 0
    && planTotalPlanned(plan) === 0
    && /domingo|sem produção/i.test(plan.warning || '');
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
        <td>${line.carriedOver ? pp.badge('CARRIED_OVER') : ''} ${line.advancedFromFuture ? pp.badge('ADVANCED') : ''}<small>${pp.escapeHtml(line.priorityExplanation || '')}</small></td>
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
  document.querySelector('#retry-planning-operation')?.addEventListener('click', async () => {
    document.querySelector('#planning-recalculate')?.click();
  });
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
  document.querySelector('#planning-refresh')?.addEventListener('click', async () => {
    state.busy.refresh = true;
    renderProductionPlanning();
    try {
      await loadAdminData();
    } finally {
      state.busy.refresh = false;
    }
    render();
  });
  document.querySelector('#planning-recalculate')?.addEventListener('click', async () => {
    state.busy.recalculate = true;
    state.operationNotice = null;
    renderProductionPlanning();
    try {
      const recalculated = await api.postJson(`/api/v1/admin/production-plans/${state.date}/recalculate`, {});
      if (recalculated) {
        state.productionPlan = recalculated;
      }
      const refreshed = await loadPlanningData();
      state.operationNotice = refreshed
        ? { type: 'success', message: 'Plano recalculado com sucesso.' }
        : operationError(`Plano recalculado, mas não foi possível recarregar o planeamento de ${pp.formatDate(state.date)}.`, state.loadError);
    } catch (error) {
      state.operationNotice = operationError(`Não foi possível recalcular o plano de ${pp.formatDate(state.date)}.`, error);
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
  if (!Number.isInteger(minimumDailyTarget) || minimumDailyTarget < 0) {
    pp.showMessage(message, 'O target mínimo não pode ser negativo.');
    return;
  }
  if (!Number.isInteger(regularDailyCapacity) || regularDailyCapacity <= 0) {
    pp.showMessage(message, 'O target máximo deve ser superior a zero.');
    return;
  }
  if (minimumDailyTarget > regularDailyCapacity) {
    pp.showMessage(message, 'O target mínimo não pode ser superior ao target máximo.');
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
  const summaryPlan = dailySummaryPlan(d);
  const items = plan?.items || [];
  document.querySelector('#admin-app').innerHTML = `
    ${dailyDashboardSummary(d, summaryPlan)}
    <section class="metric-grid metric-grid-secondary">
      ${metric('Na fábrica', d.wheelsAtFactory)}
      ${metric('Esperadas hoje', d.wheelsExpectedToday)}
      ${metric('Em produção', d.wheelsInProduction)}
      ${metric('Prontas', d.wheelsReady)}
    </section>
    ${capacityAlertBanner()}
    ${summaryPlan?.warning ? `<p class="message ${isOvertimeRequired(summaryPlan) ? 'error' : 'warning'}">${pp.escapeHtml(summaryPlan.warning)}</p>` : ''}
    ${arrivalPendingNotice(items)}
    <p class="connection-status">Última geração: ${summaryPlan ? pp.formatDateTime(summaryPlan.generatedAt) + ' · versão ' + summaryPlan.versionNumber : 'sem plano gerado'}</p>
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

function arrivalPendingNotice(items) {
  const pending = items.filter(item => item.availabilityClassification === 'WAITING_FOR_ARRIVAL'
    || item.requestLifecycleStatus === 'COMMUNICATED');
  if (!pending.length) return '';
  const total = pending.reduce((sum, item) => sum + (item.quantity || item.plannedQuantity || 0), 0);
  const codes = pending.map(item => item.requestCode || '-').join(', ');
  return `<div class="message warning">
    <strong>Existem ${pending.length} pedidos planeados cuja chegada ainda não foi confirmada.</strong><br>
    Total pendente de confirmação: ${total} jantes.<br>
    Pedidos: ${pp.escapeHtml(codes)}.<br>
    Estas jantes só podem entrar em produção depois de ser confirmada a sua chegada à fábrica.
    <div class="actions" style="margin-top:8px">
      <a class="button secondary" href="#factory-arrivals">Validar chegadas</a>
      <button type="button" class="secondary" onclick="regeneratePlan()">Recalcular plano</button>
    </div>
  </div>`;
}

function dailySummaryPlan(d) {
  if (state.productionPlan?.planningDate === d.date) {
    return state.productionPlan;
  }
  return d.plan;
}

function dailyDashboardSummary(d, plan) {
  return `<section class="metric-grid metric-grid-dashboard-primary">
    ${metric('Total planeado', `${planTotalPlanned(plan, d)} jantes`)}
    ${metric('Total concluído', `${planTotalCompleted(plan)} jantes`)}
    ${metric('Target mínimo', targetMetricValue(planMinimumTarget(plan), plan))}
    ${metric('Target máximo', targetMetricValue(planMaximumTarget(plan), plan))}
  </section>
  <section class="metric-grid metric-grid-dashboard-secondary">
    ${metric('Excesso acima do target máximo', `${planExcessQuantity(plan)} jantes`)}
    ${overtimeMetric(plan)}
    ${metric('Trabalho transportado', `${planCarriedOver(plan)} jantes`)}
    ${metric('Última atualização', plan?.generatedAt ? pp.formatDateTime(plan.generatedAt) : '-')}
  </section>`;
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
        <select id="filter-status"><option value="">Todos os estados</option>${['COMMUNICATED','AT_FACTORY','IN_PRODUCTION','READY_FOR_PICKUP','CANCELLED'].map(status => `<option ${state.filters.status === status ? 'selected' : ''}>${status}</option>`).join('')}</select>
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
        <td>${pp.badge(item.availabilityClassification)}${item.availabilityClassification === 'WAITING_FOR_ARRIVAL' ? '<br><small>Chegada por confirmar</small>' : ''}</td>
        <td>${pp.badge(item.riskClassification)}</td>
        <td>${pp.escapeHtml(item.priorityExplanation)}</td>
        <td><div class="actions">
          ${request ? `<button type="button" class="secondary" data-priority="${request.id}">Prioridade</button>` : ''}
        </div></td>
      </tr>`;
    }).join('')}</tbody>
  </table></div>`;
}

function bindDashboardActions() {
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
  state.operationNotice = null;
  const button = document.querySelector('#regenerate-button');
  if (button) {
    button.disabled = true;
    button.textContent = 'A gerar...';
  }
  try {
    const generated = await api.postJson(`/api/v1/admin/production-plans/${state.date}/recalculate`, {});
    if (generated) {
      state.productionPlan = generated;
    }
    const refreshed = await loadPlanningData();
    await loadOptionalAdminData();
    state.operationNotice = refreshed
      ? { type: 'success', message: 'Plano gerado com sucesso.' }
      : operationError(`Plano gerado, mas não foi possível recarregar o planeamento de ${pp.formatDate(state.date)}.`, state.loadError);
    render();
  } catch (error) {
    state.operationNotice = operationError('Não foi possível gerar o plano de produção.', error);
    render();
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
  const summary = [
    'Confirmar entrada na fábrica?',
    '',
    `Pedido: ${request.requestCode || '-'}`,
    `Cliente: ${request.customerNameSnapshot || '-'}`,
    `Motorista: ${request.driverName || '-'}`,
    wheelSummaryText(request.wheelQuantities),
    `Total: ${request.totalQuantity || request.expectedWheelQuantity || 0} jantes`,
    `Entrada prevista: ${pp.formatDateTime(request.expectedFactoryDropOffWindowStart)}–${pp.formatDateTime(request.expectedFactoryDropOffWindowEnd)}`
  ].join('\n');
  if (!confirm(summary)) return;
  await api.postJson(`/api/v1/admin/factory-arrivals/${id}/confirm`, {
    actualFactoryArrivalAt: new Date().toISOString(),
    reason: 'Confirmado na Entrada na Fábrica',
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
        <thead><tr><th>Pedido</th><th>Cliente</th><th>Motorista</th><th>Tipos de jantes</th><th>Deixar na fábrica</th><th>Levantar na fábrica</th><th>Estado</th><th>Origem</th><th>Recebidas</th><th>Diferença</th></tr></thead>
        <tbody>${state.requests.map(request => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(request.requestCode || '-')}</span></td>
          <td>${pp.escapeHtml(request.customerNameSnapshot)}</td>
          <td>${pp.escapeHtml(request.driverName)}</td>
          <td>${wheelSummaryVertical(request.wheelQuantities)}</td>
          <td>${factoryWindow(request.expectedFactoryDropOffWindowStart, request.expectedFactoryDropOffWindowEnd)}</td>
          <td>${factoryWindow(request.requestedFactoryPickupWindowStart, request.requestedFactoryPickupWindowEnd)}</td>
          <td>${pp.badge(request.lifecycleStatus)}</td>
          <td>${pp.escapeHtml(request.source || '-')}</td>
          <td>${request.actualReceivedWheelQuantity ?? '-'}</td>
          <td>${request.quantityDiscrepancy ? pp.badge('AT_RISK') : '-'}</td>
        </tr>`).join('')}</tbody>
      </table></div>
    </section>`;
}

function renderFactoryArrivals() {
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel">
      <div class="section-header">
        <div>
          <h2>Entrada na Fábrica</h2>
          <p class="muted">Confirmação da chegada dos pedidos comunicados.</p>
        </div>
      </div>
      ${state.factoryArrivals.length ? `<div class="table-wrap"><table class="requests-table">
        <thead><tr><th>Pedido</th><th>Cliente</th><th>Motorista</th><th>Chegada prevista</th><th>Levantamento pretendido</th><th>Tipos</th><th>Total</th><th>Estado</th><th>Situação</th><th></th></tr></thead>
        <tbody>${state.factoryArrivals.map(request => `<tr>
          <td><span class="code-pill">${pp.escapeHtml(request.requestCode || '-')}</span></td>
          <td>${pp.escapeHtml(request.customerNameSnapshot || '-')}</td>
          <td>${pp.escapeHtml(request.driverName || '-')}</td>
          <td>${factoryWindow(request.expectedFactoryDropOffWindowStart, request.expectedFactoryDropOffWindowEnd)}</td>
          <td>${factoryWindow(request.requestedFactoryPickupWindowStart, request.requestedFactoryPickupWindowEnd)}</td>
          <td>${wheelSummaryVertical(request.wheelQuantities)}</td>
          <td>${request.totalQuantity || request.expectedWheelQuantity || 0}</td>
          <td>${pp.badge(request.lifecycleStatus)}</td>
          <td>${arrivalSituation(request)}</td>
          <td><button type="button" data-arrival="${request.id}">Confirmar chegada</button></td>
        </tr>`).join('')}</tbody>
      </table></div>` : '<p class="muted">Não existem pedidos comunicados a aguardar entrada na fábrica.</p>'}
    </section>`;
  document.querySelectorAll('[data-arrival]').forEach(button => button.addEventListener('click', () => confirmArrival(button.dataset.arrival)));
}

function renderSettingsSection() {
  const section = activeSettingsSection();
  const content = section === 'audit'
    ? auditSettingsMarkup()
    : section === 'capacity'
      ? capacitySettingsMarkup()
      : diagnosticsSettingsMarkup();
  document.querySelector('#admin-app').innerHTML = `
    <section class="panel settings-shell">
      <div class="section-header">
        <div>
          <p class="eyebrow">Definições</p>
          <h2>Definições &gt; ${settingsSectionLabel(section)}</h2>
        </div>
      </div>
      <nav class="settings-subnav" aria-label="Secções de definições">
        ${settingsSubnavLink('diagnostics', 'Diagnóstico do Sistema', section)}
        ${settingsSubnavLink('audit', 'Auditoria', section)}
        ${settingsSubnavLink('capacity', 'Capacidade', section)}
      </nav>
    </section>
    ${content}`;
  if (section === 'diagnostics') {
    bindDiagnosticsActions();
  }
}

function activeSettingsSection() {
  const section = state.view.split('/')[1] || 'diagnostics';
  return ['diagnostics', 'audit', 'capacity'].includes(section) ? section : 'diagnostics';
}

function settingsSectionLabel(section) {
  return {
    diagnostics: 'Diagnóstico do Sistema',
    audit: 'Auditoria',
    capacity: 'Capacidade'
  }[section] || 'Diagnóstico do Sistema';
}

function settingsSubnavLink(section, label, activeSection) {
  return `<a href="#settings/${section}" class="${section === activeSection ? 'active' : ''}">${pp.escapeHtml(label)}</a>`;
}

function capacitySettingsMarkup() {
  return `
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
    renderSettingsSection();
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
      ${pageNotice()}
      <form id="driver-form" class="form-grid">
        <label>ID RucoFi opcional<input name="rucofiId"></label>
        <label>Nome<input name="name" required></label>
        <label>Utilizador<input name="username"></label>
        <label>Palavra-passe<input name="password" type="password"></label>
        <button type="submit">Criar motorista</button>
      </form>
      <div class="table-wrap" style="margin-top:12px"><table><thead><tr><th>Código RucoPlan</th><th>Nome</th><th>ID RucoFi</th><th>Identidade Telegram</th><th>Contacto</th><th>Estado</th><th>Primeira ligação</th><th>Última atividade</th><th>Ações</th></tr></thead><tbody>
        ${state.drivers.map(driver => {
          const identity = driverPrimaryIdentity(driver.id);
          return `<tr>
          <td><span class="code-pill">${pp.escapeHtml(driver.driverCode || '-')}</span></td>
          <td>${pp.escapeHtml(driver.name)}</td>
          <td>${pp.escapeHtml(driver.rucofiId || '-')}</td>
          <td>${identity ? `${pp.escapeHtml(identity.externalUsername ? '@' + identity.externalUsername : identity.externalUserId || '-')}` : '-'}</td>
          <td>${pp.escapeHtml(identity?.maskedPhoneNumber || '-')}</td>
          <td>${driver.active ? 'Ativo' : 'Inativo'}</td>
          <td>${identity ? pp.formatDateTime(identity.firstSeenAt) : '-'}</td>
          <td>${identity ? pp.formatDateTime(identity.lastSeenAt) : '-'}</td>
          <td><button type="button" class="secondary" data-driver-rename="${driver.id}">Corrigir nome</button></td>
        </tr>`;
        }).join('')}
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

function driverPrimaryIdentity(driverId) {
  return state.messagingIdentities
    .filter(identity => identity.driverId === driverId)
    .sort((left, right) => new Date(right.lastSeenAt || 0) - new Date(left.lastSeenAt || 0))[0];
}

async function createDriver(event) {
  event.preventDefault();
  const form = event.currentTarget;
  await api.postJson('/api/v1/admin/drivers', {
    rucofiId: form.rucofiId.value.trim(),
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
    rucofiId: driver.rucofiId || '',
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
    state.pageNotice = { type: 'error', message: 'Motorista não encontrado.' };
    renderDrivers();
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
      ${pageNotice()}
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
    state.pageNotice = { type: 'error', message: 'Cliente não encontrado.' };
    renderCustomers();
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
  document.querySelector('#admin-app').innerHTML = auditSettingsMarkup();
}

function auditSettingsMarkup() {
  return `
    <section class="panel">
      <div class="section-header"><h2>Auditoria</h2></div>
      <div class="table-wrap"><table><thead><tr><th>Data</th><th>Evento</th><th>Ator</th><th>Detalhe</th></tr></thead><tbody>
        ${state.audit.map(event => `<tr><td>${pp.formatDateTime(event.createdAt)}</td><td>${pp.escapeHtml(event.eventType)}</td><td>${pp.escapeHtml(event.actor)}</td><td>${pp.escapeHtml(event.detail)}</td></tr>`).join('')}
      </tbody></table></div>
    </section>`;
}

function renderDiagnostics() {
  document.querySelector('#admin-app').innerHTML = diagnosticsSettingsMarkup();
  bindDiagnosticsActions();
}

function diagnosticsSettingsMarkup() {
  const diagnostics = state.diagnostics;
  const realtime = diagnostics?.realtime || {};
  const database = diagnostics?.database || {};
  const backend = diagnostics?.backend || {};
  const planning = diagnostics?.planning || {};
  const diagnosticsAvailable = !!diagnostics && !state.diagnosticsError;
  return `
    <section class="panel">
      <div class="section-header">
        <div>
          <h2>Diagnóstico do sistema</h2>
          <p class="muted">Informação técnica sanitizada para apoio ao troubleshooting.</p>
        </div>
        <div class="actions">
          <button id="retry-realtime-diagnostics" type="button" class="secondary">Tentar restabelecer ligação</button>
          <button id="refresh-diagnostics" type="button" class="secondary">Atualizar diagnóstico</button>
          <button id="reload-planning-data" type="button" class="secondary">Recarregar dados do planeamento</button>
          <button id="copy-diagnostics" type="button">Copiar resumo de diagnóstico</button>
        </div>
      </div>
      ${diagnosticsNotice()}
      <section class="metric-grid metric-grid-secondary">
        ${metric('Tempo real', state.realtime.status)}
        ${metric('Transporte', realtime.transport || 'SSE')}
        ${metric('Clientes SSE ativos', diagnosticsAvailable ? (realtime.activeClients ?? 0) : 'Indisponível')}
        ${metric('Base de dados', diagnosticsAvailable ? (database.status || 'UNKNOWN') : databaseUnavailableLabel())}
        ${metric('Backend', diagnosticsAvailable ? (backend.status || 'UNKNOWN') : backendUnavailableLabel())}
        ${metric('Pedidos confirmados', diagnosticCount(planning.confirmedRequests))}
        ${metric('Planos abertos', diagnosticCount(planning.openPlans))}
        ${metric('Linhas planeadas', diagnosticCount(planning.planLines))}
      </section>
    </section>
    <section class="panel">
      <div class="section-header"><h2>Ligação em tempo real</h2></div>
      ${diagnosticsTable([
        ['Estado atual', state.realtime.status],
        ['Estado backend SSE', realtime.status || 'UNKNOWN'],
        ['Endpoint', realtime.endpoint || '/api/v1/admin/dashboard/stream'],
        ['Última ligação bem-sucedida', pp.formatDateTime(state.realtime.lastConnectedAt || realtime.lastConnectedAt)],
        ['Última mensagem', pp.formatDateTime(state.realtime.lastEventAt || realtime.lastEventAt)],
        ['Última falha', pp.formatDateTime(state.realtime.lastFailureAt || realtime.lastFailureAt)],
        ['Tentativas', state.realtime.attempts],
        ['Próxima tentativa', pp.formatDateTime(state.realtime.nextRetryAt || realtime.nextRetryAt)],
        ['HTTP status', realtime.httpStatus || '-'],
        ['Código do erro', state.realtime.errorCode || realtime.errorCode || '-'],
        ['Correlation ID', state.realtime.correlationId || realtime.correlationId || '-'],
        ['Heartbeat', state.realtime.heartbeatStatus || realtime.heartbeatStatus || 'UNKNOWN'],
        ['Último heartbeat', pp.formatDateTime(realtime.lastHeartbeatAt)]
      ])}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Backend e base de dados</h2></div>
      ${diagnosticsTable([
        ['Backend acessível', diagnosticsAvailable ? (backend.status === 'UP' ? 'Sim' : 'Não') : backendUnavailableLabel()],
        ['Versão da aplicação', backend.version || '-'],
        ['Ambiente', backend.environment || '-'],
        ['Última resposta', pp.formatDateTime(backend.lastResponseAt)],
        ['Latência backend', backend.latencyMs == null ? '-' : `${backend.latencyMs} ms`],
        ['Endpoint de planeamento', backend.planningEndpointStatus || '-'],
        ['Estado da base de dados', diagnosticsAvailable ? (database.status || 'UNKNOWN') : databaseUnavailableLabel()],
        ['Base de dados', database.databaseName || '-'],
        ['Schema', database.schemaName || '-'],
        ['Estado do schema', database.schemaStatus || 'UNKNOWN'],
        ['Código do schema', database.schemaErrorCode || '-'],
        ['Última verificação DB', pp.formatDateTime(database.checkedAt)],
        ['Latência DB', database.latencyMs == null ? '-' : `${database.latencyMs} ms`],
        ['Código DB', database.errorCode || '-'],
        ['Correlation ID DB', database.correlationId || '-']
      ])}
    </section>
    <section class="panel">
      <div class="section-header"><h2>Dados do planeamento</h2></div>
      ${diagnosticsTable([
        ['Pedidos confirmados', diagnosticCount(planning.confirmedRequests)],
        ['Erro nos pedidos confirmados', planning.confirmedRequestsErrorCode || '-'],
        ['Planos abertos', diagnosticCount(planning.openPlans)],
        ['Erro nos planos abertos', planning.openPlansErrorCode || '-'],
        ['Linhas planeadas', diagnosticCount(planning.planLines)],
        ['Erro nas linhas planeadas', planning.planLinesErrorCode || '-'],
        ['Plano mais recente', planning.latestPlanDate ? pp.formatDate(planning.latestPlanDate) : '-'],
        ['Erro no plano mais recente', planning.latestPlanDateErrorCode || '-'],
        ['Última execução', pp.formatDateTime(planning.lastPlanningRunAt)],
        ['Estado da última execução', planning.lastPlanningRunStatus || '-'],
        ['Motivo da última execução', planning.lastPlanningRunTrigger || '-'],
        ['Erro na última execução', planning.lastPlanningRunErrorCode || '-'],
        ['Targets usados', planning.targetsUsed
          ? `${planning.targetsUsed.minimumDailyTarget}/${planning.targetsUsed.regularDailyCapacity} (${planning.targetsUsed.source})`
          : '-'],
        ['Erro nos targets', planning.targetsErrorCode || '-'],
        ['Correlation ID planeamento', planning.correlationId || '-']
      ])}
    </section>`;
}

async function connectStream() {
  const current = api.session();
  if (!('EventSource' in window) || !current?.token) {
    setConnectionState('OFFLINE');
    return;
  }
  cleanupRealtime(false);
  state.realtime.manuallyStopped = false;
  setConnectionState(state.realtime.attempts > 0 ? 'RECONNECTING' : 'CONNECTING');
  try {
    await api.postJson('/api/v1/auth/stream-session', {});
  } catch (error) {
    state.realtime.attempts += 1;
    state.realtime.lastFailureAt = new Date().toISOString();
    state.realtime.errorCode = normalizeError(error).code || 'REALTIME_AUTH_FAILED';
    state.realtime.correlationId = normalizeError(error).correlationId || createCorrelationId();
    setConnectionState('FAILED');
    return;
  }
  state.stream = new EventSource('/api/v1/admin/dashboard/stream');
  state.stream.addEventListener('open', () => {
    state.realtime.attempts = 0;
    state.realtime.lastConnectedAt = new Date().toISOString();
    state.realtime.errorCode = null;
    state.realtime.correlationId = null;
    setConnectionState('CONNECTED');
    refreshAdminData();
  });
  state.stream.addEventListener('dashboard-connected', () => {
    state.realtime.attempts = 0;
    state.realtime.lastConnectedAt = new Date().toISOString();
    state.realtime.lastEventAt = new Date().toISOString();
    state.realtime.heartbeatStatus = 'ACTIVE';
    state.realtime.errorCode = null;
    state.realtime.correlationId = null;
    setConnectionState('CONNECTED');
    refreshAdminData();
  });
  state.stream.addEventListener('production-plan-updated', async event => {
    try {
      state.realtime.lastEventAt = new Date().toISOString();
      const payload = JSON.parse(event.data);
      if (payload.date === state.date) {
        await loadAdminData();
        render();
      }
    } catch (_) {
      // A malformed or stale event must not break the REST fallback.
    }
  });
  state.stream.addEventListener('error', () => {
    if (state.realtime.manuallyStopped) return;
    state.stream?.close();
    state.stream = null;
    state.realtime.attempts += 1;
    state.realtime.lastFailureAt = new Date().toISOString();
    state.realtime.errorCode = 'REALTIME_CONNECTION_FAILED';
    state.realtime.correlationId = createCorrelationId();
    if (state.realtime.attempts >= 5) {
      state.realtime.nextRetryAt = null;
      setConnectionState('FAILED');
      return;
    }
    setConnectionState('RECONNECTING');
    const delay = Math.min(1000 * (2 ** (state.realtime.attempts - 1)), 15000);
    state.realtime.nextRetryAt = new Date(Date.now() + delay).toISOString();
    window.clearTimeout(state.realtime.retryTimer);
    state.realtime.retryTimer = window.setTimeout(connectStream, delay);
  });
}

function startFallbackPolling() {
  window.clearInterval(state.fallbackTimer);
  state.fallbackTimer = window.setInterval(async () => {
    if (state.realtime.status === 'CONNECTED') {
      return;
    }
    try {
      await loadAdminData();
      render();
    } catch (_) {
      // REST fallback errors are handled by the normal screen actions.
      // They must not overwrite the SSE connection state.
    }
  }, 30000);
}

async function refreshAdminData() {
  try {
    await loadAdminData();
    render();
  } catch (_) {
    // Keep the current screen usable; explicit user actions show request errors.
  }
}

function cleanupRealtime(markStopped = true) {
  if (markStopped) state.realtime.manuallyStopped = true;
  window.clearTimeout(state.realtime.retryTimer);
  state.realtime.retryTimer = null;
  if (state.stream) {
    state.stream.close();
    state.stream = null;
  }
}

function cleanupAdminConnections() {
  cleanupRealtime();
  window.clearInterval(state.fallbackTimer);
  state.fallbackTimer = null;
}

function setConnectionState(status) {
  state.realtime.status = status;
  const element = document.querySelector('#connection-status');
  if (!element) return;
  element.className = 'connection-status';
  element.classList.toggle('hidden', status === 'CONNECTING' || status === 'CONNECTED');
  if (status === 'CONNECTING' || status === 'CONNECTED') {
    element.textContent = status === 'CONNECTED' ? 'Ligado em tempo real' : '';
    return;
  }
  if (status === 'RECONNECTING') {
    element.classList.add('message', 'warning');
    element.textContent = 'A tentar reconectar...';
    return;
  }
  if (status === 'FAILED') {
    element.classList.add('message', 'warning');
    element.innerHTML = `Ligação em tempo real indisponível. Algumas atualizações podem necessitar de recarregamento manual. ${technicalDetailsLink()} <button type="button" class="secondary" id="retry-realtime">Tentar novamente</button>`;
    element.querySelector('#retry-realtime')?.addEventListener('click', () => {
      state.realtime.attempts = 0;
      connectStream();
    });
    return;
  }
  element.classList.add('message', 'warning');
  element.innerHTML = `Sem ligação em tempo real. Algumas atualizações podem necessitar de recarregamento manual. ${technicalDetailsLink()}`;
}

function planningSystemNotices() {
  const notices = [];
  if (state.operationNotice) {
    notices.push(renderOperationNotice(state.operationNotice));
  }
  if (state.diagnosticsError && !state.diagnostics) {
    notices.push(`<p class="message warning">${pp.escapeHtml(diagnosticsErrorMessage())} ${technicalDetailsLink()}</p>`);
  }
  if (state.targetsError && !state.loadError) {
    notices.push(`<p class="message warning">Não foi possível carregar os targets atuais. O planeamento permanece visível. ${technicalDetailsLink()}</p>`);
  }
  if (state.loadError?.code === 'BACKEND_UNAVAILABLE') {
    notices.push(`<p class="message error">Não foi possível contactar o servidor do RucoPlan. ${technicalDetailsLink()}</p>`);
  } else if (state.loadError?.code === 'UNAUTHORIZED' || state.loadError?.code === 'AUTHENTICATION_REQUIRED') {
    notices.push(`<p class="message error">A sessão expirou ou não foi possível autenticar a chamada ao backend. Inicie sessão novamente.</p>`);
  } else if (state.loadError?.code === 'FORBIDDEN' || state.loadError?.code === 'ACCESS_DENIED') {
    notices.push(`<p class="message error">Não tem permissões para consultar o planeamento. ${technicalDetailsLink()}</p>`);
  } else if (state.loadError?.code === 'DATABASE_UNAVAILABLE' || state.diagnostics?.database?.status === 'DOWN') {
    notices.push(`<p class="message error">Não foi possível estabelecer ligação à base de dados. Os dados do planeamento não podem ser consultados neste momento. ${technicalDetailsLink()}</p>`);
  } else if (state.noPlanningData) {
    notices.push('<p class="message info">Não existem dados de planeamento para apresentar. Ainda não existem pedidos confirmados ou planos de produção.</p>');
  }
  return notices.join('');
}

function operationError(title, error) {
  const normalized = normalizeError(error);
  return {
    type: 'error',
    title,
    message: normalized.message,
    code: normalized.code,
    status: normalized.status,
    correlationId: normalized.correlationId,
    endpoint: normalized.endpoint,
    timestamp: normalized.timestamp || new Date().toISOString()
  };
}

function renderOperationNotice(notice) {
  if (notice.type === 'success') {
    return `<p class="message success">${pp.escapeHtml(notice.message)}</p>`;
  }
  return `<div class="message error">
    <strong>${pp.escapeHtml(notice.title || 'Operação falhou.')}</strong>
    <span>${pp.escapeHtml(notice.message || 'Erro inesperado.')}</span>
    <small>Código: ${pp.escapeHtml(notice.code || '-')} · HTTP: ${pp.escapeHtml(notice.status ?? '-')} · Endpoint: ${pp.escapeHtml(notice.endpoint || '-')} · ${pp.formatDateTime(notice.timestamp)}${notice.correlationId ? ` · Correlation ID: ${pp.escapeHtml(notice.correlationId)}` : ''}</small>
    ${technicalDetailsLink()}
    <button type="button" class="secondary" id="retry-planning-operation">Tentar novamente</button>
  </div>`;
}

function pageNotice() {
  if (!state.pageNotice) {
    return '';
  }
  return `<p class="message ${pp.escapeHtml(state.pageNotice.type || 'info')}">${pp.escapeHtml(state.pageNotice.message || '')}</p>`;
}

function normalizeError(error) {
  return {
    message: error?.message || 'Erro inesperado.',
    code: error?.code || 'UNKNOWN_ERROR',
    status: error?.status ?? null,
    correlationId: error?.correlationId || null,
    endpoint: error?.endpoint || null,
    timestamp: error?.timestamp || new Date().toISOString()
  };
}

function updatePlanningDataState() {
  const planning = state.diagnostics?.planning;
  state.noPlanningData = !!planning
    && state.diagnostics?.database?.status === 'UP'
    && !state.loadError
    && planning.confirmedRequests === 0
    && planning.openPlans === 0
    && planning.planLines === 0;
}

function diagnosticCount(value) {
  return value === null || value === undefined ? 'Indisponível' : value;
}

function backendUnavailableLabel() {
  if (!state.diagnosticsError) return 'UNKNOWN';
  if (state.diagnosticsError.code === 'BACKEND_UNAVAILABLE') return 'Não foi possível contactar';
  if (state.diagnosticsError.status === 401) return 'Não autorizado';
  if (state.diagnosticsError.status === 403) return 'Sem permissões';
  return state.diagnosticsError.status ? `HTTP ${state.diagnosticsError.status}` : 'Desconhecido';
}

function databaseUnavailableLabel() {
  if (!state.diagnosticsError) return 'UNKNOWN';
  if (state.diagnosticsError.code === 'BACKEND_UNAVAILABLE') {
    return 'Não foi possível verificar porque o backend está inacessível';
  }
  return 'Não foi possível verificar';
}

function diagnosticsErrorMessage() {
  if (!state.diagnosticsError) return '';
  if (state.diagnosticsError.code === 'BACKEND_UNAVAILABLE') {
    return 'Não foi possível contactar o servidor do RucoPlan.';
  }
  if (state.diagnosticsError.status === 401) {
    return 'Não foi possível confirmar o diagnóstico porque a sessão expirou.';
  }
  if (state.diagnosticsError.status === 403) {
    return 'Não foi possível confirmar o diagnóstico por falta de permissões.';
  }
  return 'Não foi possível confirmar o estado técnico do backend.';
}

function diagnosticsNotice() {
  if (!state.diagnosticsError || state.diagnostics) {
    return '';
  }
  const detail = state.diagnosticsError.correlationId
    ? ` <small>Correlation ID: ${pp.escapeHtml(state.diagnosticsError.correlationId)}</small>`
    : '';
  return `<p class="message warning">${pp.escapeHtml(diagnosticsErrorMessage())}${detail}</p>`;
}

function technicalDetailsLink() {
  return '<a class="technical-link" href="/admin/system-diagnostics/realtime#settings/diagnostics" target="_blank" rel="noopener noreferrer">Ver detalhes técnicos</a>';
}

function diagnosticsTable(rows) {
  return `<div class="table-wrap"><table class="diagnostics-table"><tbody>
    ${rows.map(([label, value]) => `<tr><th>${pp.escapeHtml(label)}</th><td>${pp.escapeHtml(value ?? '-')}</td></tr>`).join('')}
  </tbody></table></div>`;
}

function bindDiagnosticsActions() {
  document.querySelector('#retry-realtime-diagnostics')?.addEventListener('click', () => {
    state.realtime.attempts = 0;
    connectStream();
    renderDiagnostics();
  });
  document.querySelector('#refresh-diagnostics')?.addEventListener('click', async () => {
    await loadAdminData();
    renderDiagnostics();
  });
  document.querySelector('#reload-planning-data')?.addEventListener('click', async () => {
    await loadAdminData();
    render();
  });
  document.querySelector('#copy-diagnostics')?.addEventListener('click', async () => {
    const summary = diagnosticsSummary();
    try {
      await navigator.clipboard.writeText(summary);
      document.querySelector('#copy-diagnostics').textContent = 'Resumo copiado';
    } catch (_) {
      window.prompt('Resumo de diagnóstico', summary);
    }
  });
}

function diagnosticsSummary() {
  const diagnostics = state.diagnostics || {};
  const realtime = diagnostics.realtime || {};
  const database = diagnostics.database || {};
  const backend = diagnostics.backend || {};
  const planning = diagnostics.planning || {};
  const unavailable = value => value === null || value === undefined ? '-' : value;
  return [
    'RucoPlan diagnostics',
    `Realtime status: ${state.realtime.status}`,
    `Backend version: ${backend.version || '-'}`,
    `Backend status: ${backend.status || state.diagnosticsError?.code || state.loadError?.code || 'UNKNOWN'}`,
    `Realtime transport: ${realtime.transport || 'SSE'}`,
    `Realtime backend status: ${realtime.status || 'UNKNOWN'}`,
    `Last connected at: ${state.realtime.lastConnectedAt || realtime.lastConnectedAt || '-'}`,
    `Last error code: ${state.realtime.errorCode || realtime.errorCode || '-'}`,
    `Database status: ${database.status || 'UNKNOWN'}`,
    `Schema status: ${database.schemaStatus || 'UNKNOWN'}`,
    `Confirmed requests: ${unavailable(planning.confirmedRequests)}`,
    `Confirmed requests error: ${planning.confirmedRequestsErrorCode || '-'}`,
    `Open plans: ${unavailable(planning.openPlans)}`,
    `Open plans error: ${planning.openPlansErrorCode || '-'}`,
    `Plan lines: ${unavailable(planning.planLines)}`,
    `Plan lines error: ${planning.planLinesErrorCode || '-'}`,
    `Last planning run status: ${planning.lastPlanningRunStatus || '-'}`,
    `Targets: ${planning.targetsUsed ? `${planning.targetsUsed.minimumDailyTarget}/${planning.targetsUsed.regularDailyCapacity}` : '-'}`,
    `Correlation ID: ${state.realtime.correlationId || realtime.correlationId || database.correlationId || planning.correlationId || state.loadError?.correlationId || state.diagnosticsError?.correlationId || '-'}`
  ].join('\n');
}

function createCorrelationId() {
  return window.crypto?.randomUUID ? window.crypto.randomUUID() : `client-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function syncNavigation() {
  document.querySelectorAll('.nav a').forEach(link => {
    const target = link.getAttribute('href')?.replace('#', '') || '';
    const active = state.view === target || (target.startsWith('settings/') && state.view.startsWith('settings/'));
    link.classList.toggle('active', active);
  });
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
  return state.requests.find(request => request.id === id)
    || state.factoryArrivals.find(request => request.id === id);
}

function wheelSummary(quantities = []) {
  const bipartite = quantityValue(quantities, 'BIPARTITE');
  const washed = quantityValue(quantities, 'WASHED');
  const normal = quantityValue(quantities, 'NORMAL');
  return `${bipartite} bipartidas · ${washed} lavadas · ${normal} normais`;
}

function wheelSummaryText(quantities = []) {
  return `Bipartidas: ${quantityValue(quantities, 'BIPARTITE')}\nLavadas: ${quantityValue(quantities, 'WASHED')}\nNormais: ${quantityValue(quantities, 'NORMAL')}`;
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

function arrivalSituation(request) {
  const now = new Date();
  const start = new Date(request.expectedFactoryDropOffWindowStart);
  const end = new Date(request.expectedFactoryDropOffWindowEnd);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return pp.badge('MISSING_INFORMATION');
  }
  if (now < start) {
    return '<span class="muted">Aguardando chegada prevista</span>';
  }
  if (now <= end) {
    return pp.badge('WAITING_FOR_ARRIVAL');
  }
  return `${pp.badge('OVERDUE')}<br><small>Chegada por confirmar</small>`;
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
