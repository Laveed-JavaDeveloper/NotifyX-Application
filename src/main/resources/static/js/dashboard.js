'use strict';

const API = '';
let currentPage = 0;
let statusFilter = '';
let rateLimitCount = 0;
let sseSource = null;

// ── SSE Connection ────────────────────────
function connectSSE() {
  if (sseSource) sseSource.close();
  sseSource = new EventSource(`${API}/api/stream`);

  sseSource.addEventListener('connected', () => {
    setConnectionStatus(true);
  });

  sseSource.addEventListener('STATUS_CHANGE', (e) => {
    try {
      const event = JSON.parse(e.data);
      addFeedItem(event);
      loadStats();
      refreshTableRow(event.notificationId, event.status);
    } catch {}
  });

  sseSource.onerror = () => {
    setConnectionStatus(false);
    setTimeout(connectSSE, 3000);
  };
}

function setConnectionStatus(connected) {
  const badge = document.getElementById('connectionBadge');
  const status = document.getElementById('connectionStatus');
  if (connected) {
    badge.className = 'connection-badge connected';
    status.textContent = 'Connected';
  } else {
    badge.className = 'connection-badge disconnected';
    status.textContent = 'Reconnecting...';
  }
}

// ── Live Feed ─────────────────────────────
function addFeedItem(event) {
  const feed = document.getElementById('liveFeed');
  const empty = document.getElementById('feedEmpty');
  if (empty) empty.remove();

  const dotClass = {
    DELIVERED: 'feed-dot-delivered',
    PENDING: 'feed-dot-pending',
    FAILED_RETRYING: 'feed-dot-retrying',
    DLQ: 'feed-dot-dlq'
  }[event.status] || 'feed-dot-pending';

  const item = document.createElement('div');
  item.className = 'feed-item';
  item.id = `feed-${event.notificationId}`;
  item.innerHTML = `
    <div class="feed-dot ${dotClass}"></div>
    <div class="feed-body">
      <div class="feed-main">${escHtml(event.message || event.status)}</div>
      <div class="feed-meta">${escHtml(event.recipient || '')} · ${escHtml(event.templateId || '')}</div>
    </div>
    <div class="feed-time">${new Date().toLocaleTimeString()}</div>
  `;
  feed.insertBefore(item, feed.firstChild);

  // Cap at 50 items
  const items = feed.querySelectorAll('.feed-item');
  if (items.length > 50) items[items.length - 1].remove();
}

document.getElementById('clearFeedBtn').addEventListener('click', () => {
  const feed = document.getElementById('liveFeed');
  feed.innerHTML = `<div class="feed-empty" id="feedEmpty">
    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3"><path d="M22 12h-4l-3 9L9 3l-3 9H2"/></svg>
    <p>Waiting for events...</p><span>Send a notification to see real-time updates</span>
  </div>`;
});

// ── Stats ─────────────────────────────────
async function loadStats() {
  try {
    const r = await fetch(`${API}/api/notifications/stats`);
    const s = await r.json();
    animateCount('statTotalVal', s.total);
    animateCount('statDeliveredVal', s.delivered);
    animateCount('statRetryingVal', s.failedRetrying);
    animateCount('statDlqVal', s.dlq);
    const rate = s.total > 0 ? Math.round((s.delivered / s.total) * 100) : 0;
    document.getElementById('statDeliveredRate').textContent = `${rate}% success rate`;
  } catch {}
}

function animateCount(id, val) {
  const el = document.getElementById(id);
  if (!el) return;
  el.textContent = val;
  el.classList.remove('updated');
  void el.offsetWidth;
  el.classList.add('updated');
}

// ── Send Form ─────────────────────────────
document.getElementById('sendForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  await sendNotification();
});

async function sendNotification(overrideRecipient) {
  const btn = document.getElementById('sendBtn');
  const txt = document.getElementById('sendBtnText');
  const spin = document.getElementById('sendSpinner');
  const result = document.getElementById('sendResult');

  let payload;
  try { payload = JSON.parse(document.getElementById('payloadInput').value); }
  catch { showToast('Invalid JSON payload', 'error'); return; }

  txt.textContent = 'Sending...';
  spin.classList.remove('hidden');
  btn.disabled = true;
  result.className = 'send-result hidden';

  try {
    const r = await fetch(`${API}/api/notifications`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-User-Id': 'dashboard-user' },
      body: JSON.stringify({
        recipient: overrideRecipient || document.getElementById('recipient').value,
        templateId: document.getElementById('templateId').value,
        payload
      })
    });
    const data = await r.json();
    if (r.ok) {
      result.className = 'send-result success';
      result.textContent = `✅ Queued: ${data.notificationId?.substring(0, 8)}...`;
      updateRateLimit(rateLimitCount + 1);
      showToast('Notification queued!', 'success');
      loadTable();
    } else if (r.status === 429) {
      result.className = 'send-result error';
      result.textContent = '🚫 Rate limit exceeded! Max 5 per minute.';
      updateRateLimit(5);
      showToast('Rate limit hit! Try again in 60s.', 'warning');
    } else {
      result.className = 'send-result error';
      result.textContent = `❌ ${data.message || 'Error'}`;
      showToast(data.message || 'Failed', 'error');
    }
  } catch (err) {
    result.className = 'send-result error';
    result.textContent = '❌ Network error';
    showToast('Network error', 'error');
  } finally {
    txt.textContent = 'Send Notification';
    spin.classList.add('hidden');
    btn.disabled = false;
  }
}

// ── Burst Test ────────────────────────────
document.getElementById('burstBtn').addEventListener('click', async () => {
  showToast('Sending 6 rapid requests to trigger rate limiter...', 'info');
  for (let i = 0; i < 6; i++) {
    await sendNotification(`burst-test-${i}@notifyx.io`);
    await sleep(200);
  }
});

// ── Rate Limit Bar ────────────────────────
function updateRateLimit(count) {
  rateLimitCount = Math.min(count, 5);
  const fill = document.getElementById('rateLimitFill');
  const label = document.getElementById('rateLimitUsed');
  const pct = (rateLimitCount / 5) * 100;
  fill.style.width = `${pct}%`;
  fill.className = `rate-limit-fill${rateLimitCount >= 4 ? ' warning' : ''}`;
  label.textContent = rateLimitCount;
  if (rateLimitCount >= 5) {
    setTimeout(() => { rateLimitCount = 0; updateRateLimit(0); }, 60000);
  }
}

// ── Table ─────────────────────────────────
async function loadTable() {
  const tbody = document.getElementById('tableBody');
  try {
    const params = new URLSearchParams({ page: currentPage, size: 15 });
    if (statusFilter) params.set('status', statusFilter);
    const r = await fetch(`${API}/api/notifications?${params}`);
    const data = await r.json();
    renderTable(data.content || []);
    renderPagination(data.totalPages || 0, data.currentPage || 0);
  } catch {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#71717a;padding:24px">Failed to load notifications</td></tr>';
  }
}

function renderTable(rows) {
  const tbody = document.getElementById('tableBody');
  if (!rows.length) {
    tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#71717a;padding:24px">No notifications yet — send one!</td></tr>';
    return;
  }
  tbody.innerHTML = rows.map(n => `
    <tr id="row-${n.id}">
      <td><div class="id-cell" title="${escHtml(n.id)}">${n.id?.substring(0, 8)}...</div></td>
      <td>${escHtml(n.recipient || '')}</td>
      <td><code style="font-size:11px;color:#a1a1aa">${escHtml(n.templateId || '')}</code></td>
      <td><span class="status-badge status-${n.status}">${n.status}</span></td>
      <td style="text-align:center">${n.retryCount}</td>
      <td style="font-size:11px;color:#71717a">${formatDate(n.createdAt)}</td>
      <td>
        <div class="table-actions">
          ${n.status !== 'DELIVERED' ? `<button class="action-btn" onclick="retryNotification('${n.id}')">Retry</button>` : ''}
          <button class="action-btn danger" onclick="deleteNotification('${n.id}')">Del</button>
        </div>
      </td>
    </tr>
  `).join('');
}

function refreshTableRow(id, status) {
  const row = document.getElementById(`row-${id}`);
  if (!row) { loadTable(); return; }
  const badge = row.querySelector('.status-badge');
  if (badge) {
    badge.className = `status-badge status-${status}`;
    badge.textContent = status;
    row.style.background = 'rgba(99,102,241,.05)';
    setTimeout(() => row.style.background = '', 800);
  }
}

function renderPagination(totalPages, currentP) {
  const pg = document.getElementById('tablePagination');
  if (totalPages <= 1) { pg.innerHTML = ''; return; }
  let html = '';
  for (let i = 0; i < totalPages; i++) {
    html += `<button class="page-btn${i === currentP ? ' active' : ''}" onclick="goPage(${i})">${i + 1}</button>`;
  }
  pg.innerHTML = html;
}

function goPage(p) { currentPage = p; loadTable(); }

async function retryNotification(id) {
  await fetch(`${API}/api/notifications/${id}/retry`, { method: 'POST' });
  showToast('Re-queued for delivery!', 'info');
  loadTable();
}

async function deleteNotification(id) {
  await fetch(`${API}/api/notifications/${id}`, { method: 'DELETE' });
  showToast('Notification deleted', 'info');
  loadTable(); loadStats();
}

document.getElementById('statusFilter').addEventListener('change', (e) => {
  statusFilter = e.target.value; currentPage = 0; loadTable();
});
document.getElementById('refreshTableBtn').addEventListener('click', loadTable);

// ── Utilities ─────────────────────────────
function showToast(msg, type = 'info') {
  const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
  const container = document.getElementById('toastContainer');
  const t = document.createElement('div');
  t.className = `toast toast-${type}`;
  t.innerHTML = `<span class="toast-icon">${icons[type]}</span><span class="toast-msg">${escHtml(msg)}</span><button class="toast-close" onclick="this.parentElement.remove()">×</button>`;
  container.appendChild(t);
  setTimeout(() => t.remove(), 4000);
}

function escHtml(s) {
  return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatDate(d) {
  if (!d) return '—';
  return new Date(d).toLocaleString();
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ── Init ──────────────────────────────────
connectSSE();
loadStats();
loadTable();
setInterval(loadStats, 10000);
setInterval(loadTable, 15000);
