// Thin API client. The backend uses HTTP Basic, so credentials are held in
// memory for the session and attached to every request. They are deliberately
// not persisted — a compliance console should not leave credentials in
// localStorage.

let credentials = null;

export function setCredentials(username, password) {
  credentials = btoa(`${username}:${password}`);
}

export function clearCredentials() {
  credentials = null;
}

export function hasCredentials() {
  return credentials !== null;
}

async function request(path, options = {}) {
  const response = await fetch(`/api/v1${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(credentials ? { Authorization: `Basic ${credentials}` } : {}),
      ...(options.headers || {}),
    },
  });

  if (response.status === 204) return null;

  const body = await response.json().catch(() => null);
  if (!response.ok) {
    // The API returns RFC 7807 ProblemDetail, so there is always a usable
    // message — surface it rather than a generic failure.
    throw new Error(body?.detail || body?.title || `HTTP ${response.status}`);
  }
  return body;
}

export const api = {
  stats: () => request('/dashboard/stats'),
  heatmap: () => request('/dashboard/heatmap'),
  productivity: () => request('/dashboard/productivity'),

  alerts: (params = {}) => {
    const q = new URLSearchParams({
      size: 50, sortBy: 'riskScore', direction: 'desc',
      ...Object.fromEntries(Object.entries(params).filter(([, v]) => v)),
    });
    return request(`/alerts?${q}`);
  },
  alert: (ref) => request(`/alerts/${ref}`),
  transitionAlert: (ref, body) =>
    request(`/alerts/${ref}/status`, { method: 'PATCH', body: JSON.stringify(body) }),

  timeline: (customerId, limit = 100) =>
    request(`/customers/${customerId}/timeline?limit=${limit}`),
  customer: (customerId) => request(`/customers/${customerId}`),

  cases: (params = {}) => {
    const q = new URLSearchParams({ size: 50, ...params });
    return request(`/cases?${q}`);
  },
  caseDetail: (ref) => request(`/cases/${ref}`),
  openCase: (body) => request('/cases', { method: 'POST', body: JSON.stringify(body) }),
  transitionCase: (ref, body) =>
    request(`/cases/${ref}/status`, { method: 'PATCH', body: JSON.stringify(body) }),

  // SAR drafting — SENIOR_ANALYST only; an ANALYST receives 403, which the UI surfaces.
  sarDraft: (ref) => request(`/cases/${ref}/sar-draft`),
};
