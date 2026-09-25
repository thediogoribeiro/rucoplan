const SESSION_KEY = 'productionPlanning.session';

function session() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null');
  } catch (_) {
    return null;
  }
}

function saveSession(value) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(value));
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

function createCorrelationId() {
  return window.crypto?.randomUUID ? window.crypto.randomUUID() : `client-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json, application/problem+json');
  if (!headers.has('X-Correlation-ID')) {
    headers.set('X-Correlation-ID', createCorrelationId());
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const current = session();
  if (options.auth !== false && current?.token) {
    headers.set('Authorization', `Bearer ${current.token}`);
  }
  let response;
  try {
    response = await fetch(path, { ...options, headers, credentials: options.credentials || 'same-origin' });
  } catch (error) {
    const networkError = new Error('Não foi possível contactar o servidor do RucoPlan.');
    networkError.code = 'BACKEND_UNAVAILABLE';
    networkError.status = 0;
    networkError.endpoint = path;
    networkError.correlationId = headers.get('X-Correlation-ID');
    throw networkError;
  }
  if (response.status === 401) {
    clearSession();
    if (!location.pathname.endsWith('/login.html')) location.replace('login.html');
  }
  if (!response.ok) {
    let message = response.status === 403
      ? 'Não tem permissões para executar esta operação.'
      : 'Erro inesperado.';
    let payload = null;
    try {
      payload = await response.json();
      message = [payload.detail, payload.message, ...(payload.details || [])].filter(Boolean).join(' ');
    } catch (_) {}
    const error = new Error(message);
    error.code = payload?.code
      || payload?.error
      || (response.status === 401 ? 'UNAUTHORIZED' : response.status === 403 ? 'FORBIDDEN' : response.status === 503 ? 'BACKEND_UNAVAILABLE' : 'HTTP_ERROR');
    error.correlationId = payload?.correlationId || response.headers.get('X-Correlation-ID') || headers.get('X-Correlation-ID');
    error.status = response.status;
    error.endpoint = path;
    error.timestamp = payload?.timestamp || new Date().toISOString();
    error.payload = payload;
    throw error;
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

const api = {
  session,
  saveSession,
  clearSession,
  get(path) { return request(path); },
  postJson(path, body, options = {}) {
    return request(path, { ...options, method: 'POST', body: JSON.stringify(body) });
  },
  patchJson(path, body) {
    return request(path, { method: 'PATCH', body: JSON.stringify(body) });
  },
  putJson(path, body) {
    return request(path, { method: 'PUT', body: JSON.stringify(body) });
  },
  requireRole(role) {
    const current = session();
    if (!current?.token || current.user?.role !== role) {
      location.replace('login.html');
      return null;
    }
    return current;
  },
  logout() {
    request('/api/v1/auth/logout', { method: 'POST' }).catch(() => null).finally(() => {
      clearSession();
      location.replace('login.html');
    });
  }
};

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return new Intl.DateTimeFormat('pt-PT', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function formatDate(value) {
  if (!value) return '-';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
}

function fullDate(value) {
  if (!value) return '-';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('pt-PT', {
    timeZone: 'Europe/Lisbon',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).format(date);
}

function toOffsetDateTime(datetimeLocal) {
  if (!datetimeLocal) return null;
  const date = new Date(datetimeLocal);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function todayString() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

function addDays(value, days) {
  const date = new Date(`${value}T00:00:00`);
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function tomorrowString() {
  return addDays(todayString(), 1);
}

function datetimeLocalValue(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const statusLabels = {
  COMMUNICATED: 'Comunicado',
  AT_FACTORY: 'Na fábrica',
  IN_PRODUCTION: 'Em produção',
  READY_FOR_PICKUP: 'Pronto',
  CANCELLED: 'Cancelado',
  TENTATIVE: 'Tentativo',
  WAITING_FOR_ARRIVAL: 'A aguardar chegada',
  ON_TRACK: 'Controlado',
  AT_RISK: 'Em risco',
  OVERDUE: 'Atrasado',
  OVER_CAPACITY: 'Excesso',
  MISSING_INFORMATION: 'Info. em falta'
  ,
  DRAFT: 'Rascunho',
  PROVISIONAL: 'Provisório',
  PUBLISHED: 'Publicado',
  IN_PROGRESS: 'Em curso',
  AWAITING_RECONCILIATION: 'Por validar',
  CLOSED: 'Fechado',
  PLANNED: 'Planeado',
  PARTIALLY_COMPLETED: 'Parcial',
  COMPLETED: 'Concluído',
  CARRIED_OVER: 'Transportado',
  ADVANCED: 'Antecipado',
  OVERTIME_REQUIRED: 'Horas extra'
};

function badge(value) {
  return `<span class="status ${escapeHtml(value)}">${escapeHtml(statusLabels[value] || value || '-')}</span>`;
}

function showMessage(element, message, type = 'error') {
  element.className = `message ${type}`;
  element.textContent = message;
  element.classList.remove('hidden');
}

function hideMessage(element) {
  element.classList.add('hidden');
}

window.api = api;
window.pp = { escapeHtml, formatDateTime, formatDate, fullDate, toOffsetDateTime, todayString, tomorrowString, addDays, datetimeLocalValue, badge, showMessage, hideMessage };
