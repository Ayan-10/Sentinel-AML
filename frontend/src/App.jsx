import React, { useEffect, useState } from 'react';
import { api, setCredentials, clearCredentials, hasCredentials } from './api.js';
import AlertQueue from './views/AlertQueue.jsx';
import Heatmap from './views/Heatmap.jsx';
import Timeline from './views/Timeline.jsx';
import Cases from './views/Cases.jsx';
import { ErrorBox } from './shared.jsx';

const DEMO_USERS = [
  { username: 'analyst', password: 'analyst123', role: 'ANALYST' },
  { username: 'senior',  password: 'senior123',  role: 'SENIOR_ANALYST' },
  { username: 'admin',   password: 'admin123',   role: 'COMPLIANCE_ADMIN' },
];

const TABS = [
  { id: 'alerts',   label: 'Alert queue' },
  { id: 'heatmap',  label: 'Risk heatmap' },
  { id: 'timeline', label: 'Customer timeline' },
  { id: 'cases',    label: 'Cases' },
];

function Login({ onSignIn }) {
  const [selected, setSelected] = useState(0);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const user = DEMO_USERS[selected];
    setCredentials(user.username, user.password);
    try {
      // Prove the credentials work before entering the console, so a bad
      // password surfaces here rather than as an empty screen later.
      await api.stats();
      onSignIn(user);
    } catch (err) {
      clearCredentials();
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="login" onSubmit={submit}>
      <h1>Sentinel AML</h1>
      <p>Transaction monitoring console — MeridianTrust Bank</p>
      <ErrorBox message={error} />
      <label htmlFor="user">Sign in as</label>
      <select id="user" value={selected} onChange={(e) => setSelected(Number(e.target.value))}>
        {DEMO_USERS.map((u, i) => (
          <option key={u.username} value={i}>{u.username} — {u.role}</option>
        ))}
      </select>
      <button className="primary" type="submit" disabled={busy}>
        {busy ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  );
}

export default function App() {
  const [user, setUser] = useState(null);
  const [tab, setTab] = useState('alerts');
  const [stats, setStats] = useState(null);
  const [focusCustomer, setFocusCustomer] = useState('');

  useEffect(() => {
    if (user) api.stats().then(setStats).catch(() => {});
  }, [user, tab]);

  if (!user || !hasCredentials()) return <Login onSignIn={setUser} />;

  /** Jumping from a heatmap cell or an alert straight to that customer's timeline. */
  function openTimeline(customerId) {
    setFocusCustomer(customerId);
    setTab('timeline');
  }

  return (
    <>
      <header className="topbar">
        <div className="brand">Sentinel AML<span>Transaction Monitoring</span></div>
        <nav className="tabs">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`tab ${tab === t.id ? 'active' : ''}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <div className="whoami">
          {user.username} · {user.role}
          <button onClick={() => { clearCredentials(); setUser(null); }}>Sign out</button>
        </div>
      </header>

      <main className="page">
        {stats && (
          <div className="stats">
            <div className="stat"><div className="value">{stats.totalAlerts.toLocaleString()}</div><div className="label">Alerts</div></div>
            <div className="stat"><div className="value">{(stats.alertsByStatus?.OPEN ?? 0).toLocaleString()}</div><div className="label">Open</div></div>
            <div className="stat"><div className="value">{stats.totalCases.toLocaleString()}</div><div className="label">Cases</div></div>
            <div className="stat"><div className="value">{stats.totalTransactions.toLocaleString()}</div><div className="label">Transactions</div></div>
            <div className="stat"><div className="value">{stats.totalCustomers.toLocaleString()}</div><div className="label">Customers</div></div>
            <div className="stat"><div className="value">{stats.activeRules?.length ?? 0}</div><div className="label">Active rules</div></div>
          </div>
        )}

        {tab === 'alerts'   && <AlertQueue onOpenTimeline={openTimeline} />}
        {tab === 'heatmap'  && <Heatmap onOpenTimeline={openTimeline} />}
        {tab === 'timeline' && <Timeline customerId={focusCustomer} onCustomerChange={setFocusCustomer} />}
        {tab === 'cases'    && <Cases />}
      </main>
    </>
  );
}
