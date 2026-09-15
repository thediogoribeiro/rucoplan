document.addEventListener('DOMContentLoaded', async () => {
  const current = api.requireRole('ADMIN');
  if (!current) return;
  const params = new URLSearchParams(location.search);
  const date = params.get('date') || pp.todayString();
  document.querySelector('#print-now').addEventListener('click', () => window.print());
  const plan = await api.get(`/api/v1/admin/plans?date=${encodeURIComponent(date)}`);
  document.querySelector('#print-app').innerHTML = `
    <section class="panel">
      <div class="section-header"><h2>${pp.formatDate(date)} · versão ${plan.versionNumber}</h2></div>
      <section class="metric-grid">
        <div class="metric"><span>Jantes planeadas</span><strong>${plan.totalPlanned}</strong></div>
        <div class="metric"><span>Em risco</span><strong>${plan.totalAtRisk}</strong></div>
        <div class="metric"><span>Excesso</span><strong>${plan.totalOverCapacity}</strong></div>
        <div class="metric"><span>A aguardar</span><strong>${plan.totalWaitingForArrival}</strong></div>
        <div class="metric"><span>Carga futura</span><strong>${plan.totalFutureWorkload}</strong></div>
      </section>
      ${plan.warning ? `<p class="message warning">${pp.escapeHtml(plan.warning)}</p>` : ''}
      <table>
        <thead><tr><th>Prioridade</th><th>Cliente</th><th>Motorista</th><th>Jantes</th><th>Janela</th><th>Risco</th><th>Explicação</th></tr></thead>
        <tbody>${plan.items.map(item => `<tr>
          <td>${item.priorityScore}</td>
          <td>${pp.escapeHtml(item.customerName)}</td>
          <td>${pp.escapeHtml(item.driverName)}</td>
          <td>${item.quantity}</td>
          <td>${pp.escapeHtml(item.assignedWindowLabel || 'Sem janela')}</td>
          <td>${pp.badge(item.riskClassification)}</td>
          <td>${pp.escapeHtml(item.priorityExplanation)}</td>
        </tr>`).join('')}</tbody>
      </table>
    </section>
  `;
});
