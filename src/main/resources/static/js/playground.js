'use strict';

const API = '';
let selectedTemplate = 'otp_template';
const templatePayloads = {
  otp_template: { otp: '847291', expiry: '10 minutes' },
  welcome_template: { name: 'John Doe', plan: 'Pro' },
  alert_template: { title: 'Suspicious Login', severity: 'HIGH', location: 'Mumbai, India' }
};

function selectTemplate(el) {
  document.querySelectorAll('.template-card').forEach(c => c.classList.remove('active'));
  el.classList.add('active');
  selectedTemplate = el.dataset.template;
  document.getElementById('pgPayload').value = JSON.stringify(templatePayloads[selectedTemplate], null, 2);
  updateCurl();
}

function formatJson() {
  try {
    const val = JSON.parse(document.getElementById('pgPayload').value);
    document.getElementById('pgPayload').value = JSON.stringify(val, null, 2);
  } catch { showToast('Invalid JSON', 'error'); }
}

function resetPayload() {
  document.getElementById('pgPayload').value = JSON.stringify(templatePayloads[selectedTemplate], null, 2);
  updateCurl();
}

function updateCurl() {
  const recipient = document.getElementById('pgRecipient').value;
  const userId = document.getElementById('pgUserId').value;
  let payload = document.getElementById('pgPayload').value;
  const host = window.location.host;
  const body = JSON.stringify({ recipient, templateId: selectedTemplate, payload: JSON.parse(payload) });
  document.getElementById('curlCode').textContent =
    `curl -X POST http://${host}/api/notifications \\\n  -H "Content-Type: application/json" \\\n  -H "X-User-Id: ${userId}" \\\n  -d '${body}'`;
}

async function sendRequest() {
  const btn = document.getElementById('pgSendBtn');
  const txt = document.getElementById('pgSendText');
  const spin = document.getElementById('pgSpinner');
  const resBlock = document.getElementById('responseBlock');
  const resMeta = document.getElementById('pgResponseMeta');

  let payload;
  try { payload = JSON.parse(document.getElementById('pgPayload').value); }
  catch { showToast('Invalid JSON payload', 'error'); return; }

  txt.textContent = 'Sending...';
  spin.classList.remove('hidden');
  btn.disabled = true;
  resBlock.innerHTML = '<div style="color:#71717a;padding:16px">Waiting for response...</div>';
  resMeta.innerHTML = '';

  const start = Date.now();
  try {
    const r = await fetch(`${API}/api/notifications`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': document.getElementById('pgUserId').value || 'playground-user'
      },
      body: JSON.stringify({
        recipient: document.getElementById('pgRecipient').value,
        templateId: selectedTemplate,
        payload
      })
    });
    const elapsed = Date.now() - start;
    const data = await r.json();
    const cls = r.status < 300 ? '2xx' : r.status < 500 ? '4xx' : '5xx';
    resMeta.innerHTML = `<span class="status-code status-${cls}">${r.status}</span><span style="font-size:11px;color:#71717a">${elapsed}ms</span>`;
    resBlock.innerHTML = `<pre style="margin:0;white-space:pre-wrap;word-break:break-word">${escHtml(JSON.stringify(data, null, 2))}</pre>`;
    if (r.ok) showToast('Request successful!', 'success');
    else if (r.status === 429) showToast('Rate limit hit!', 'warning');
    else showToast(data.message || 'Request failed', 'error');
  } catch (err) {
    resBlock.innerHTML = `<span style="color:#ef4444">Network error: ${escHtml(err.message)}</span>`;
    showToast('Network error', 'error');
  } finally {
    txt.textContent = 'Send Request';
    spin.classList.add('hidden');
    btn.disabled = false;
    updateCurl();
  }
}

function copyCurl() {
  const text = document.getElementById('curlCode').textContent;
  navigator.clipboard.writeText(text).then(() => showToast('cURL copied!', 'success'));
}

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

document.getElementById('pgPayload').addEventListener('input', updateCurl);
document.getElementById('pgRecipient').addEventListener('input', updateCurl);
document.getElementById('pgUserId').addEventListener('input', updateCurl);
updateCurl();
