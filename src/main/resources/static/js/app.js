// State Management
const state = {
  token: localStorage.getItem('token') || null,
  user: localStorage.getItem('username') || null,
  categories: [],
  paymentModes: [],
  currentPage: 0,
  pageSize: 10,
  totalPages: 1,
  chartInstances: {},
  currentTab: 'dashboard'
};

const API_BASE = '/api/v1';

// DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  initApp();
});

function initApp() {
  bindEvents();
  if (state.token) {
    showApp();
    loadInitialData();
  } else {
    showAuth();
  }
}

function bindEvents() {
  // Navigation Tabs
  document.querySelectorAll('.nav-item[data-tab]').forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      switchTab(item.dataset.tab);
    });
  });

  // Auth form handlers
  document.getElementById('loginForm')?.addEventListener('submit', handleLogin);
  document.getElementById('registerForm')?.addEventListener('submit', handleRegister);
  document.getElementById('logoutBtn')?.addEventListener('click', handleLogout);
  document.getElementById('switchAuthMode')?.addEventListener('click', toggleAuthMode);

  // Natural Language Entry Form
  document.getElementById('nlExpenseForm')?.addEventListener('submit', handleNLEntry);

  // Natural Language Query Form
  document.getElementById('nlQueryForm')?.addEventListener('submit', handleNLQuery);

  // Manual Expense Form
  document.getElementById('manualExpenseForm')?.addEventListener('submit', handleManualExpense);

  // Filters
  document.getElementById('filterStartDate')?.addEventListener('change', () => loadExpenses(0));
  document.getElementById('filterEndDate')?.addEventListener('change', () => loadExpenses(0));
  document.getElementById('filterCategory')?.addEventListener('change', () => loadExpenses(0));

  // Autocomplete on NL Input and Manual Vendor Input
  setupAutocomplete(document.getElementById('nlPromptInput'), 'vendor');
  setupAutocomplete(document.getElementById('manualVendorInput'), 'vendor');

  // AI Insights Refresh
  document.getElementById('refreshInsightsBtn')?.addEventListener('click', () => loadInsights(true));
  document.getElementById('insightDaysSelect')?.addEventListener('change', () => loadInsights());

  // Modal Triggers
  document.getElementById('openAddExpenseModalBtn')?.addEventListener('click', () => openModal('addExpenseModal'));
  document.querySelectorAll('.modal-close, .modal-overlay').forEach(el => {
    el.addEventListener('click', (e) => {
      if (e.target === el) closeModal();
    });
  });
}

function switchTab(tabId) {
  state.currentTab = tabId;
  document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
  document.querySelector(`.nav-item[data-tab="${tabId}"]`)?.classList.add('active');

  document.querySelectorAll('.tab-view').forEach(view => view.style.display = 'none');
  const activeView = document.getElementById(`view-${tabId}`);
  if (activeView) activeView.style.display = 'block';

  if (tabId === 'dashboard') {
    loadDashboardData();
  } else if (tabId === 'expenses') {
    loadExpenses(0);
  } else if (tabId === 'insights') {
    loadInsights();
  }
}

// API Helper
async function apiFetch(endpoint, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(state.token ? { 'Authorization': `Bearer ${state.token}` } : {}),
    ...options.headers
  };

  try {
    const response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers
    });

    if (response.status === 401) {
      showToast('Session expired. Please log in again.', 'error');
      handleLogout();
      return null;
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ message: response.statusText }));
      throw new Error(errorData.message || 'API request failed');
    }

    if (response.status === 204) return true;
    return await response.json();
  } catch (error) {
    showToast(error.message, 'error');
    throw error;
  }
}

// Authentication
async function handleLogin(e) {
  e.preventDefault();
  const username = document.getElementById('loginUsername').value;
  const password = document.getElementById('loginPassword').value;

  try {
    const data = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    });

    if (data && data.token) {
      state.token = data.token;
      state.user = data.username;
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
      showToast('Welcome back, ' + data.username + '!', 'success');
      showApp();
      loadInitialData();
    }
  } catch (err) {
    // Toast handled in apiFetch
  }
}

async function handleRegister(e) {
  e.preventDefault();
  const username = document.getElementById('regUsername').value;
  const email = document.getElementById('regEmail').value;
  const password = document.getElementById('regPassword').value;

  try {
    const data = await apiFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, email, password })
    });

    if (data && data.token) {
      state.token = data.token;
      state.user = data.username;
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
      showToast('Account created successfully!', 'success');
      showApp();
      loadInitialData();
    }
  } catch (err) {
    // Toast handled in apiFetch
  }
}

function handleLogout() {
  state.token = null;
  state.user = null;
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  showAuth();
}

function showAuth() {
  document.getElementById('authContainer').style.display = 'flex';
  document.getElementById('appContainer').style.display = 'none';
}

function showApp() {
  document.getElementById('authContainer').style.display = 'none';
  document.getElementById('appContainer').style.display = 'flex';
  document.getElementById('displayUsername').textContent = state.user || 'User';
  document.getElementById('userAvatarLetter').textContent = (state.user || 'U').charAt(0).toUpperCase();
}

function toggleAuthMode(e) {
  e.preventDefault();
  const loginSection = document.getElementById('loginSection');
  const registerSection = document.getElementById('registerSection');
  const switchBtn = document.getElementById('switchAuthMode');

  if (loginSection.style.display === 'none') {
    loginSection.style.display = 'block';
    registerSection.style.display = 'none';
    switchBtn.textContent = "Don't have an account? Sign Up";
  } else {
    loginSection.style.display = 'none';
    registerSection.style.display = 'block';
    switchBtn.textContent = 'Already have an account? Sign In';
  }
}

// Initial Data Load
async function loadInitialData() {
  try {
    const [cats, modes] = await Promise.all([
      apiFetch('/categories'),
      apiFetch('/payment-modes')
    ]);

    state.categories = cats || [];
    state.paymentModes = modes || [];

    populateDropdowns();
    loadDashboardData();
  } catch (err) {
    console.error('Initial data load error:', err);
  }
}

function populateDropdowns() {
  const catSelects = [document.getElementById('manualCategorySelect'), document.getElementById('filterCategory')];
  catSelects.forEach(select => {
    if (!select) return;
    const isFilter = select.id.startsWith('filter');
    select.innerHTML = isFilter ? '<option value="">All Categories</option>' : '<option value="">Select Category</option>';
    state.categories.forEach(cat => {
      const opt = document.createElement('option');
      opt.value = cat.id;
      opt.textContent = cat.name;
      select.appendChild(opt);
    });
  });

  const modeSelect = document.getElementById('manualPaymentModeSelect');
  if (modeSelect) {
    modeSelect.innerHTML = '<option value="">Select Payment Mode</option>';
    state.paymentModes.forEach(mode => {
      const opt = document.createElement('option');
      opt.value = mode.id;
      opt.textContent = mode.name;
      modeSelect.appendChild(opt);
    });
  }
}

// Dashboard Data & Charts
async function loadDashboardData() {
  try {
    const summary = await apiFetch('/expenses/analytics/summary');
    if (!summary) return;

    // KPI Cards
    document.getElementById('kpiTotalSpent').textContent = '₹' + Number(summary.totalAmount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    document.getElementById('kpiTotalTransactions').textContent = (summary.totalCount || 0) + ' txns';

    const avgDaily = summary.totalCount > 0 ? (summary.totalAmount / 30) : 0;
    document.getElementById('kpiAvgDaily').textContent = '₹' + avgDaily.toFixed(2);

    let topCategory = 'None';
    let maxCatAmount = 0;
    (summary.categoryBreakdown || []).forEach(c => {
      if (c.totalAmount > maxCatAmount) {
        maxCatAmount = c.totalAmount;
        topCategory = c.categoryName;
      }
    });
    document.getElementById('kpiTopCategory').textContent = topCategory;

    // Render Charts
    renderCategoryChart(summary.categoryBreakdown || []);
    renderPaymentModeChart(summary.paymentModeBreakdown || []);
    loadRecentExpenses();
  } catch (err) {
    console.error('Failed to load dashboard:', err);
  }
}

function renderCategoryChart(categories) {
  const ctx = document.getElementById('categoryDoughnutChart')?.getContext('2d');
  if (!ctx) return;

  if (state.chartInstances.categoryChart) {
    state.chartInstances.categoryChart.destroy();
  }

  const labels = categories.map(c => c.categoryName);
  const data = categories.map(c => c.totalAmount);
  const colors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#f43f5e', '#f59e0b',
    '#10b981', '#06b6d4', '#3b82f6', '#14b8a6', '#64748b'
  ];

  state.chartInstances.categoryChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [{
        data: data.length ? data : [1],
        backgroundColor: data.length ? colors.slice(0, data.length) : ['#334155'],
        borderWidth: 0,
        hoverOffset: 6
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 12 } } }
      },
      cutout: '70%'
    }
  });
}

function renderPaymentModeChart(modes) {
  const ctx = document.getElementById('paymentModeBarChart')?.getContext('2d');
  if (!ctx) return;

  if (state.chartInstances.paymentChart) {
    state.chartInstances.paymentChart.destroy();
  }

  const labels = modes.map(m => m.paymentModeName);
  const data = modes.map(m => m.totalAmount);

  state.chartInstances.paymentChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: labels.length ? labels : ['No Data'],
      datasets: [{
        label: 'Amount (₹)',
        data: data.length ? data : [0],
        backgroundColor: '#6366f1',
        borderRadius: 6
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: { ticks: { color: '#94a3b8' }, grid: { display: false } },
        y: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255,255,255,0.05)' } }
      },
      plugins: {
        legend: { display: false }
      }
    }
  });
}

// Recent Expenses on Dashboard
async function loadRecentExpenses() {
  const data = await apiFetch('/expenses?size=5&sort=date,desc');
  const tbody = document.getElementById('recentExpensesTableBody');
  if (!tbody || !data || !data.content) return;

  tbody.innerHTML = '';
  if (data.content.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 24px;">No expenses logged yet. Try natural language entry above!</td></tr>';
    return;
  }

  data.content.forEach(exp => {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${exp.date || '-'}</td>
      <td><strong>${escapeHtml(exp.title || exp.description || 'Expense')}</strong></td>
      <td><span class="badge badge-category">${escapeHtml(exp.category?.name || 'General')}</span></td>
      <td><span class="badge badge-payment">${escapeHtml(exp.paymentMode?.name || 'UPI/Cash')}</span></td>
      <td style="font-weight: 700; color: #fff;">₹${Number(exp.amount).toFixed(2)}</td>
    `;
    tbody.appendChild(row);
  });
}

// Full Expenses View with Pagination & Filtering
async function loadExpenses(page = 0) {
  state.currentPage = page;
  const start = document.getElementById('filterStartDate')?.value;
  const end = document.getElementById('filterEndDate')?.value;
  const cat = document.getElementById('filterCategory')?.value;

  let url = `/expenses?page=${page}&size=${state.pageSize}&sort=date,desc`;
  if (start) url += `&startDate=${start}`;
  if (end) url += `&endDate=${end}`;
  if (cat) url += `&categoryId=${cat}`;

  const data = await apiFetch(url);
  const tbody = document.getElementById('allExpensesTableBody');
  if (!tbody || !data) return;

  tbody.innerHTML = '';
  state.totalPages = data.totalPages || 1;
  document.getElementById('paginationInfo').textContent = `Page ${data.page + 1} of ${Math.max(1, data.totalPages)}`;

  document.getElementById('prevPageBtn').disabled = (data.page === 0);
  document.getElementById('nextPageBtn').disabled = data.last;

  if (!data.content || data.content.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 32px;">No matching transactions found.</td></tr>';
    return;
  }

  data.content.forEach(exp => {
    const row = document.createElement('tr');
    row.innerHTML = `
      <td>${exp.date || '-'}</td>
      <td>
        <div style="font-weight: 600; color: #fff;">${escapeHtml(exp.title || exp.description || 'Expense')}</div>
        ${exp.vendor ? `<div style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(exp.vendor)}</div>` : ''}
      </td>
      <td><span class="badge badge-category">${escapeHtml(exp.category?.name || 'Uncategorized')}</span></td>
      <td><span class="badge badge-payment">${escapeHtml(exp.paymentMode?.name || 'Other')}</span></td>
      <td style="font-weight: 700; color: #fff;">₹${Number(exp.amount).toFixed(2)}</td>
      <td>
        <button class="btn btn-danger btn-sm" onclick="deleteExpenseItem(${exp.id})">Delete</button>
      </td>
    `;
    tbody.appendChild(row);
  });
}

// Natural Language Entry
async function handleNLEntry(e) {
  e.preventDefault();
  const input = document.getElementById('nlPromptInput');
  const text = input.value.trim();
  if (!text) return;

  const btn = document.getElementById('nlSubmitBtn');
  const originalHtml = btn.innerHTML;
  btn.innerHTML = '<span>Parsing...</span>';
  btn.disabled = true;

  try {
    const res = await apiFetch('/expenses/natural-language', {
      method: 'POST',
      body: JSON.stringify({ text })
    });

    if (res) {
      showToast(`Expense added: ₹${res.amount} for ${res.title} (${res.category?.name || 'General'})`, 'success');
      input.value = '';
      if (state.currentTab === 'dashboard') {
        loadDashboardData();
      } else {
        loadExpenses(0);
      }
    }
  } catch (err) {
    // Toast handled in apiFetch
  } finally {
    btn.innerHTML = originalHtml;
    btn.disabled = false;
  }
}

// Natural Language Spending Query (Validator-Planner-Executor)
async function handleNLQuery(e) {
  e.preventDefault();
  const input = document.getElementById('nlQueryInput');
  const query = input.value.trim();
  if (!query) return;

  const resultsDiv = document.getElementById('nlQueryResultContainer');
  resultsDiv.style.display = 'block';
  resultsDiv.innerHTML = '<div style="color: var(--text-muted); padding: 12px;">Analyzing query plan and generating safe SQL AST...</div>';

  try {
    const data = await apiFetch('/expenses/query-nlp', {
      method: 'POST',
      body: JSON.stringify({ query })
    });

    if (data) {
      resultsDiv.innerHTML = `
        <div style="background-color: var(--bg-card); border: 1px solid var(--accent-primary); border-radius: 8px; padding: 16px;">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
            <strong style="color: #818cf8;">Query Analysis Result</strong>
            <span class="badge" style="background: rgba(16, 185, 129, 0.2); color: #10b981;">Tenant Isolated & Safe</span>
          </div>
          <div style="font-size: 1.1rem; font-weight: 700; color: #fff; margin-bottom: 6px;">${escapeHtml(data.answerSummary || '')}</div>
          <div style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 12px;"><strong>Intent:</strong> ${escapeHtml(data.interpretedIntent || '')}</div>
          <div style="background-color: #0b0f19; padding: 10px; border-radius: 6px; font-family: monospace; font-size: 0.8rem; color: #38bdf8; overflow-x: auto;">
            <strong>Generated Safe SQL:</strong> ${escapeHtml(data.generatedSafeSql || '')}
          </div>
        </div>
      `;
    }
  } catch (err) {
    resultsDiv.innerHTML = `<div style="color: var(--danger); padding: 12px;">Query error: ${escapeHtml(err.message)}</div>`;
  }
}

// Manual Expense Creation
async function handleManualExpense(e) {
  e.preventDefault();
  const title = document.getElementById('manualTitleInput').value;
  const amount = parseFloat(document.getElementById('manualAmountInput').value);
  const date = document.getElementById('manualDateInput').value;
  const categoryId = document.getElementById('manualCategorySelect').value || null;
  const paymentModeId = document.getElementById('manualPaymentModeSelect').value || null;
  const description = document.getElementById('manualDescriptionInput').value;

  try {
    const res = await apiFetch('/expenses', {
      method: 'POST',
      body: JSON.stringify({ title, amount, date, categoryId, paymentModeId, description })
    });

    if (res) {
      showToast('Expense created successfully!', 'success');
      closeModal();
      document.getElementById('manualExpenseForm').reset();
      if (state.currentTab === 'dashboard') loadDashboardData();
      else loadExpenses(0);
    }
  } catch (err) {
    // Toast handled in apiFetch
  }
}

// Delete Expense
async function deleteExpenseItem(id) {
  if (!confirm('Are you sure you want to delete this expense?')) return;
  try {
    await apiFetch(`/expenses/${id}`, { method: 'DELETE' });
    showToast('Expense deleted', 'success');
    loadExpenses(state.currentPage);
  } catch (err) {
    // Toast handled in apiFetch
  }
}

// AI Financial Insights
async function loadInsights(refresh = false) {
  const days = document.getElementById('insightDaysSelect')?.value || 30;
  const container = document.getElementById('aiInsightContainer');
  if (!container) return;

  container.innerHTML = '<div style="color: var(--text-muted); padding: 24px;">Generating comprehensive AI financial insights...</div>';

  try {
    const data = await apiFetch(`/expenses/insights?days=${days}`);
    if (data && data.insights) {
      container.innerHTML = `<div class="insight-content">${formatMarkdown(data.insights)}</div>`;
    }
  } catch (err) {
    container.innerHTML = `<div style="color: var(--danger); padding: 20px;">Could not generate insights: ${escapeHtml(err.message)}</div>`;
  }
}

// Autocomplete with Trie Backend
function setupAutocomplete(inputElement, type = 'vendor') {
  if (!inputElement) return;

  const wrapper = inputElement.parentElement;
  let dropdown = wrapper.querySelector('.autocomplete-dropdown');
  if (!dropdown) {
    dropdown = document.createElement('div');
    dropdown.className = 'autocomplete-dropdown';
    wrapper.appendChild(dropdown);
  }

  let timeout = null;
  inputElement.addEventListener('input', () => {
    clearTimeout(timeout);
    const prefix = inputElement.value.trim();
    if (prefix.length < 1) {
      dropdown.style.display = 'none';
      return;
    }

    timeout = setTimeout(async () => {
      try {
        const suggestions = await apiFetch(`/expenses/autocomplete?prefix=${encodeURIComponent(prefix)}&type=${type}`);
        if (suggestions && suggestions.length > 0) {
          dropdown.innerHTML = '';
          suggestions.forEach(item => {
            const div = document.createElement('div');
            div.className = 'autocomplete-item';
            div.textContent = item;
            div.addEventListener('click', () => {
              inputElement.value = item;
              dropdown.style.display = 'none';
            });
            dropdown.appendChild(div);
          });
          dropdown.style.display = 'block';
        } else {
          dropdown.style.display = 'none';
        }
      } catch (e) {
        dropdown.style.display = 'none';
      }
    }, 200);
  });

  document.addEventListener('click', (e) => {
    if (!wrapper.contains(e.target)) {
      dropdown.style.display = 'none';
    }
  });
}

// Modal Control
function openModal(modalId) {
  document.getElementById(modalId)?.classList.add('open');
  const dateInput = document.getElementById('manualDateInput');
  if (dateInput && !dateInput.value) {
    dateInput.value = new Date().toISOString().split('T')[0];
  }
}

function closeModal() {
  document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('open'));
}

// Helpers
function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&<>"']/g, m => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[m]);
}

function formatMarkdown(text) {
  if (!text) return '';
  let html = escapeHtml(text);
  // Headers
  html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>');
  html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>');
  html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>');
  // Bold
  html = html.replace(/\*\*(.*?)\*\*/gim, '<strong>$1</strong>');
  // Bullet points
  html = html.replace(/^\* (.*$)/gim, '<li>$1</li>');
  html = html.replace(/^- (.*$)/gim, '<li>$1</li>');
  // Wrap list items
  html = html.replace(/(<li>.*<\/li>)/gms, '<ul>$1</ul>');
  // Newlines
  html = html.replace(/\n\n/g, '<br><br>');
  return html;
}
