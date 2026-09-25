const state = {
  requests: [],
  customers: [],
  selectedRequest: null,
  view: 'dashboard'
};

document.addEventListener('DOMContentLoaded', async () => {
  const current = api.requireRole('DRIVER');
  if (!current) return;
  document.querySelector('#logout').addEventListener('click', api.logout);
  document.querySelectorAll('[data-view]').forEach(button => {
    button.addEventListener('click', () => {
      state.view = button.dataset.view;
      document.querySelectorAll('[data-view]').forEach(tab => tab.classList.toggle('active', tab === button));
      render();
    });
  });
  await loadDriverData();
  render();
});

async function loadDriverData() {
  const [customers, requests] = await Promise.all([
    api.get('/api/v1/customers/search?limit=50'),
    api.get('/api/v1/driver/requests?size=100')
  ]);
  state.customers = customers;
  state.requests = requests.content || [];
}

function render() {
  if (state.view === 'new') renderNewRequest();
  else if (state.view === 'list') renderRequestList();
  else renderDashboard();
}

function renderDashboard() {
  const app = document.querySelector('#driver-app');
  const active = state.requests.filter(request => request.lifecycleStatus !== 'CANCELLED');
  const upcomingDeliveries = active.filter(request => !request.actualFactoryArrivalAt)
    .sort((a, b) => new Date(a.expectedFactoryDropOffWindowStart) - new Date(b.expectedFactoryDropOffWindowStart))
    .slice(0, 5);
  const pickups = active.filter(request => ['IN_PRODUCTION', 'READY_FOR_PICKUP', 'AT_FACTORY'].includes(request.lifecycleStatus))
    .sort((a, b) => new Date(a.requestedFactoryPickupWindowStart) - new Date(b.requestedFactoryPickupWindowStart))
    .slice(0, 5);
  const warnings = active.filter(request => request.quantityDiscrepancy && !request.quantityDiscrepancyAcknowledged);

  app.innerHTML = `
    <section class="driver-summary">
      <div class="metric"><span>Pedidos abertos</span><strong>${active.length}</strong></div>
      <div class="metric"><span>Jantes registadas</span><strong>${active.reduce((sum, request) => sum + requestTotal(request), 0)}</strong></div>
    </section>
    ${warnings.length ? `<p class="message warning">Existem alterações de quantidade por validar pela administração.</p>` : ''}
    <section class="driver-card">
      <h2>Próximas entregas na fábrica</h2>
      <div class="list-stack">${upcomingDeliveries.length ? upcomingDeliveries.map(compactRequest).join('') : '<p class="muted">Sem entregas futuras registadas.</p>'}</div>
    </section>
    <section class="driver-card" style="margin-top:12px">
      <h2>Próximos levantamentos na fábrica</h2>
      <div class="list-stack">${pickups.length ? pickups.map(compactRequest).join('') : '<p class="muted">Sem levantamentos próximos.</p>'}</div>
    </section>
  `;
}

function renderNewRequest() {
  const app = document.querySelector('#driver-app');
  app.innerHTML = `
    <section class="driver-card">
      <h2>Novo pedido</h2>
      <form id="request-form" class="form-grid">
        <label class="wide">Cliente
          <select name="customerId" required>${customerOptions('')}</select>
        </label>
        ${wheelQuantityFields()}
        <p class="muted wide">Total de jantes: <strong data-wheel-total>0</strong></p>
        <label class="wide">Quando prevê deixar as jantes na fábrica? Início
          <input name="expectedFactoryDropOffWindowStart" type="datetime-local" required>
        </label>
        <label class="wide">Quando prevê deixar as jantes na fábrica? Fim
          <input name="expectedFactoryDropOffWindowEnd" type="datetime-local" required>
        </label>
        <label class="wide">Quando pretende levantar as jantes prontas na fábrica? Início
          <input name="requestedFactoryPickupWindowStart" type="datetime-local" required>
        </label>
        <label class="wide">Quando pretende levantar as jantes prontas na fábrica? Fim
          <input name="requestedFactoryPickupWindowEnd" type="datetime-local" required>
        </label>
        <label class="wide">Notas
          <textarea name="notes"></textarea>
        </label>
        <button type="submit" class="wide">Registar pedido</button>
        <p id="request-message" class="message hidden wide" role="alert"></p>
      </form>
    </section>
  `;
  document.querySelector('#request-form').addEventListener('submit', submitNewRequest);
  bindWheelQuantityTotals(document.querySelector('#request-form'));
}

async function submitNewRequest(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = document.querySelector('#request-message');
  pp.hideMessage(message);
  const payload = formPayload(form);
  if (!payload.wheelQuantities.reduce((sum, quantity) => sum + quantity.quantity, 0)) {
    pp.showMessage(message, 'O pedido tem de incluir pelo menos uma jante.');
    return;
  }
  try {
    await api.postJson('/api/v1/driver/requests', payload);
    await loadDriverData();
    state.view = 'list';
    document.querySelectorAll('[data-view]').forEach(tab => tab.classList.toggle('active', tab.dataset.view === 'list'));
    renderRequestList();
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível registar o pedido.');
  }
}

function renderRequestList() {
  const app = document.querySelector('#driver-app');
  app.innerHTML = `
    <section class="driver-card">
      <div class="section-header">
        <h2>Pedidos</h2>
        <button id="reload" type="button" class="secondary">Atualizar</button>
      </div>
      <div class="list-stack">${state.requests.length ? state.requests.map(requestListItem).join('') : '<p class="muted">Ainda não existem pedidos.</p>'}</div>
    </section>
    <div id="detail"></div>
  `;
  document.querySelector('#reload').addEventListener('click', async () => { await loadDriverData(); renderRequestList(); });
  document.querySelectorAll('[data-request-id]').forEach(button => {
    button.addEventListener('click', () => renderDetail(state.requests.find(request => request.id === button.dataset.requestId)));
  });
}

function renderDetail(request) {
  state.selectedRequest = request;
  const editable = request.lifecycleStatus === 'COMMUNICATED' && !request.actualFactoryArrivalAt;
  document.querySelector('#detail').innerHTML = `
    <section class="driver-card" style="margin-top:12px">
      <h2>${pp.escapeHtml(request.customerNameSnapshot)}</h2>
      <form id="edit-form" class="form-grid">
        <label class="wide">Cliente
          <select name="customerId" ${editable ? '' : 'disabled'}>${customerOptions(request.customerId)}</select>
        </label>
        ${wheelQuantityFields(request, editable)}
        <p class="muted wide">Total de jantes: <strong data-wheel-total>${requestTotal(request)}</strong></p>
        <label class="wide">Deixar na fábrica - início
          <input name="expectedFactoryDropOffWindowStart" type="datetime-local" value="${pp.datetimeLocalValue(request.expectedFactoryDropOffWindowStart)}" ${editable ? '' : 'disabled'}>
        </label>
        <label class="wide">Deixar na fábrica - fim
          <input name="expectedFactoryDropOffWindowEnd" type="datetime-local" value="${pp.datetimeLocalValue(request.expectedFactoryDropOffWindowEnd)}" ${editable ? '' : 'disabled'}>
        </label>
        <label class="wide">Levantar prontas na fábrica - início
          <input name="requestedFactoryPickupWindowStart" type="datetime-local" value="${pp.datetimeLocalValue(request.requestedFactoryPickupWindowStart)}" ${editable ? '' : 'disabled'}>
        </label>
        <label class="wide">Levantar prontas na fábrica - fim
          <input name="requestedFactoryPickupWindowEnd" type="datetime-local" value="${pp.datetimeLocalValue(request.requestedFactoryPickupWindowEnd)}" ${editable ? '' : 'disabled'}>
        </label>
        <label class="wide">Notas
          <textarea name="notes" ${editable ? '' : 'disabled'}>${pp.escapeHtml(request.notes || '')}</textarea>
        </label>
        <input name="version" type="hidden" value="${request.version}">
        <div class="actions wide">
          ${editable ? '<button type="submit">Guardar</button><button id="cancel-request" class="secondary" type="button">Cancelar pedido</button>' : ''}
        </div>
        <p id="edit-message" class="message hidden wide" role="alert"></p>
      </form>
    </section>
  `;
  if (editable) {
    document.querySelector('#edit-form').addEventListener('submit', submitEdit);
    document.querySelector('#cancel-request').addEventListener('click', cancelRequest);
    bindWheelQuantityTotals(document.querySelector('#edit-form'));
  }
}

async function submitEdit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const message = document.querySelector('#edit-message');
  pp.hideMessage(message);
  try {
    const payload = formPayload(form);
    if (!payload.wheelQuantities.reduce((sum, quantity) => sum + quantity.quantity, 0)) {
      pp.showMessage(message, 'O pedido tem de incluir pelo menos uma jante.');
      return;
    }
    const updated = await api.patchJson(`/api/v1/driver/requests/${state.selectedRequest.id}`, {
      ...payload,
      version: Number(form.version.value)
    });
    await loadDriverData();
    renderDetail(updated);
    pp.showMessage(message, 'Pedido atualizado.', 'success');
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível guardar.');
  }
}

async function cancelRequest() {
  const message = document.querySelector('#edit-message');
  pp.hideMessage(message);
  try {
    await api.postJson(`/api/v1/driver/requests/${state.selectedRequest.id}/cancel`, { version: state.selectedRequest.version });
    await loadDriverData();
    renderRequestList();
  } catch (error) {
    pp.showMessage(message, error.message || 'Não foi possível cancelar.');
  }
}

function formPayload(form) {
  return {
    customerId: form.customerId.value,
    wheelQuantities: wheelQuantitiesPayload(form),
    expectedFactoryDropOffWindowStart: pp.toOffsetDateTime(form.expectedFactoryDropOffWindowStart.value),
    expectedFactoryDropOffWindowEnd: pp.toOffsetDateTime(form.expectedFactoryDropOffWindowEnd.value),
    requestedFactoryPickupWindowStart: pp.toOffsetDateTime(form.requestedFactoryPickupWindowStart.value),
    requestedFactoryPickupWindowEnd: pp.toOffsetDateTime(form.requestedFactoryPickupWindowEnd.value),
    notes: form.notes.value.trim()
  };
}

function wheelQuantityFields(request = null, editable = true) {
  const disabled = editable ? '' : 'disabled';
  return `
    <label>Jantes bipartidas
      <input name="bipartiteQuantity" data-wheel-quantity type="number" min="0" step="1" value="${quantityByType(request, 'BIPARTITE')}" ${disabled} required>
    </label>
    <label>Jantes lavadas
      <input name="washedQuantity" data-wheel-quantity type="number" min="0" step="1" value="${quantityByType(request, 'WASHED')}" ${disabled} required>
    </label>
    <label>Jantes normais
      <input name="normalQuantity" data-wheel-quantity type="number" min="0" step="1" value="${quantityByType(request, 'NORMAL')}" ${disabled} required>
      <span class="help-text">Restantes jantes que não são bipartidas nem lavadas.</span>
    </label>
  `;
}

function bindWheelQuantityTotals(form) {
  const update = () => {
    const total = [...form.querySelectorAll('[data-wheel-quantity]')]
      .reduce((sum, input) => sum + Number(input.value || 0), 0);
    form.querySelector('[data-wheel-total]').textContent = total;
  };
  form.querySelectorAll('[data-wheel-quantity]').forEach(input => input.addEventListener('input', update));
  update();
}

function wheelQuantitiesPayload(form) {
  return [
    { type: 'BIPARTITE', quantity: Number(form.bipartiteQuantity.value || 0) },
    { type: 'WASHED', quantity: Number(form.washedQuantity.value || 0) },
    { type: 'NORMAL', quantity: Number(form.normalQuantity.value || 0) }
  ];
}

function quantityByType(request, type) {
  const found = request?.wheelQuantities?.find(quantity => quantity.type === type);
  return found?.quantity ?? 0;
}

function requestTotal(request) {
  return request?.totalQuantity ?? request?.expectedWheelQuantity ?? 0;
}

function wheelBreakdown(request) {
  return `${quantityByType(request, 'BIPARTITE')} bipartidas · ${quantityByType(request, 'WASHED')} lavadas · ${quantityByType(request, 'NORMAL')} normais`;
}

function customerOptions(selectedId) {
  return state.customers.map(customer => `
    <option value="${customer.id}" ${customer.id === selectedId ? 'selected' : ''}>${pp.escapeHtml(customer.name)}</option>
  `).join('');
}

function requestListItem(request) {
  return `
    <button type="button" class="list-item" data-request-id="${request.id}">
      <span class="row-between"><strong>${pp.escapeHtml(request.customerNameSnapshot)}</strong>${pp.badge(request.lifecycleStatus)}</span>
      <span>Total: ${requestTotal(request)} jantes · ${wheelBreakdown(request)}</span>
      <span class="muted">entrega ${pp.formatDateTime(request.expectedFactoryDropOffWindowStart)}-${pp.formatDateTime(request.expectedFactoryDropOffWindowEnd)}</span>
      <span class="muted">levantamento ${pp.formatDateTime(request.requestedFactoryPickupWindowStart)}</span>
    </button>
  `;
}

function compactRequest(request) {
  return `
    <article class="list-item">
      <div class="row-between"><strong>${pp.escapeHtml(request.customerNameSnapshot)}</strong>${pp.badge(request.lifecycleStatus)}</div>
      <div>Total: ${requestTotal(request)} jantes · ${wheelBreakdown(request)}</div>
      <div class="muted">Entrega: ${pp.formatDateTime(request.expectedFactoryDropOffWindowStart)} a ${pp.formatDateTime(request.expectedFactoryDropOffWindowEnd)}</div>
      <div class="muted">Levantamento: ${pp.formatDateTime(request.requestedFactoryPickupWindowStart)} a ${pp.formatDateTime(request.requestedFactoryPickupWindowEnd)}</div>
    </article>
  `;
}
