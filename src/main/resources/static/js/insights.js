(() => {
  'use strict';

  if (window.__trustVaultInsightsLoaded) return;
  window.__trustVaultInsightsLoaded = true;

  const style = document.createElement('style');
  style.textContent = `
    #tv-insights-launcher {
      position: fixed; right: 22px; bottom: 22px; z-index: 99999;
      border: 0; border-radius: 12px; padding: 13px 18px;
      background: #172554; color: white; font-weight: 700;
      box-shadow: 0 8px 24px #0002; cursor: pointer;
    }
    #tv-insights-panel {
      position: fixed; right: 22px; bottom: 78px; z-index: 99999;
      width: min(390px, calc(100vw - 32px)); max-height: 70vh;
      overflow: auto; padding: 20px; border: 1px solid #dbe3ef;
      border-radius: 16px; background: var(--card, #fff); color: var(--text, #172033);
      box-shadow: 0 12px 40px #0003; font: 14px/1.5 system-ui, sans-serif;
    }
    #tv-insights-panel[hidden] { display: none; }
    #tv-insights-panel h3 { margin: 0 0 8px; font-size: 18px; }
    #tv-insights-panel button {
      padding: 9px 12px; margin: 5px 5px 5px 0; border-radius: 8px;
      border: 1px solid #cbd5e1; cursor: pointer; background: #f8fafc;
      color: #172033; font-weight: 600;
    }
    #tv-insights-panel .tv-stat {
      display: inline-block; vertical-align: top; width: 43%;
      margin: 7px 2% 7px 0; padding: 10px;
      background: #f1f5f9; border-radius: 9px;
    }
    #tv-insights-panel .tv-number { display:block; font-size:22px; font-weight:800; }
    #tv-insights-panel .tv-note { color: #64748b; font-size:12px; }
    #tv-insights-panel .tv-alert { padding:10px; margin:8px 0; border-radius:8px; background:#fff7ed; }
    #tv-insights-panel canvas { width:100%; height:100px; display:block; }
  `;
  document.head.appendChild(style);

  const launcher = document.createElement('button');
  launcher.id = 'tv-insights-launcher';
  launcher.type = 'button';
  launcher.textContent = 'TrustVault Insights';

  const panel = document.createElement('section');
  panel.id = 'tv-insights-panel';
  panel.hidden = true;
  panel.setAttribute('aria-label', 'TrustVault insights');
  panel.innerHTML = `
    <h3>TrustVault Insights</h3>
    <p class="tv-note">Local page analysis · refresh after changing filters</p>
    <div id="tv-insights-stats"></div>
    <h4>Activity overview</h4>
    <canvas id="tv-insights-chart" width="340" height="100"
      aria-label="Visible table activity overview"></canvas>
    <div id="tv-insights-alerts"></div>
    <button type="button" id="tv-insights-refresh">Refresh analysis</button>
    <button type="button" id="tv-insights-export">Export visible table CSV</button>
    <button type="button" id="tv-insights-close">Close</button>
    <p class="tv-note">Exports only rows currently present in this page. This is not a complete server-side audit export.</p>
  `;

  document.body.append(launcher, panel);

  function visible(element) {
    return !!(element.getClientRects().length &&
      getComputedStyle(element).visibility !== 'hidden');
  }

  function getRows() {
    return [...document.querySelectorAll('table')]
      .filter(visible)
      .flatMap(table => [...table.querySelectorAll('tr')])
      .filter(visible)
      .map(row => [...row.querySelectorAll('th,td')]
        .map(cell => (cell.innerText || cell.textContent || '')
          .replace(/\s+/g, ' ').trim()))
      .filter(row => row.some(Boolean));
  }

  function downloadCsv() {
    const rows = getRows();
    if (!rows.length) {
      alert('No visible table rows were found on this page.');
      return;
    }

    const csv = '\uFEFF' + rows.map(row =>
      row.map(value => '"' + value.replace(/"/g, '""') + '"').join(',')
    ).join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'trustvault-visible-table-' +
      new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function drawChart(total, flagged, other) {
    const canvas = panel.querySelector('#tv-insights-chart');
    const ctx = canvas.getContext('2d');
    const width = canvas.width;
    const height = canvas.height;
    ctx.clearRect(0, 0, width, height);

    const values = [total, flagged, other];
    const labels = ['Visible rows', 'Refused/fail', 'Other rows'];
    const max = Math.max(1, ...values);
    const colors = ['#64748b', '#ea580c', '#2563eb'];

    values.forEach((value, index) => {
      const y = 8 + index * 31;
      ctx.fillStyle = '#e2e8f0';
      ctx.fillRect(100, y, 190, 17);
      ctx.fillStyle = colors[index];
      ctx.fillRect(100, y, Math.round(190 * value / max), 17);
      ctx.fillStyle = '#334155';
      ctx.font = '12px system-ui';
      ctx.fillText(labels[index], 2, y + 12);
      ctx.fillText(String(value), 300, y + 12);
    });
  }

  function refresh() {
    const rows = getRows();
    const normalized = rows.map(row => row.join(' ').toLowerCase());
    const header = rows[0] || [];
    const dataRows = Math.max(0, rows.length - 1);
    const failedRows = normalized.filter(text =>
      /\b(refused|denied|failure|failed|unauthorized|forbidden)\b/.test(text)
    ).length;

    const stat = (label, value) =>
      `<div class="tv-stat"><span class="tv-number">${value}</span>${label}</div>`;

    panel.querySelector('#tv-insights-stats').innerHTML =
      stat('Visible table rows', dataRows) +
      stat('Detected column headings', header.length) +
      stat('Rows mentioning refusal/failure', failedRows) +
      stat('Other visible rows', Math.max(0, dataRows - failedRows));

    const alerts = panel.querySelector('#tv-insights-alerts');
    if (failedRows >= 3) {
      alerts.innerHTML = `<div class="tv-alert"><strong>Review activity</strong><br>
        ${failedRows} visible rows mention a refused or failed action.
        Review the underlying audit records. This is a heuristic, not a verified security alert.</div>`;
    } else {
      alerts.innerHTML = `<p class="tv-note">No repeated refusal pattern detected in the visible table rows. This does not establish that the account or system is safe.</p>`;
    }

    drawChart(dataRows, failedRows, Math.max(0, dataRows - failedRows));
  }

  launcher.addEventListener('click', () => {
    panel.hidden = !panel.hidden;
    if (!panel.hidden) refresh();
  });
  panel.querySelector('#tv-insights-refresh').addEventListener('click', refresh);
  panel.querySelector('#tv-insights-export').addEventListener('click', downloadCsv);
  panel.querySelector('#tv-insights-close').addEventListener('click', () => {
    panel.hidden = true;
  });
})();
