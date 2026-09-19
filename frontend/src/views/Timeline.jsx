import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { money, when, Loading, ErrorBox } from '../shared.jsx';

/**
 * Customer transaction timeline. Transactions that are evidence for an alert are
 * marked, so an analyst sees flagged activity in the context of everything
 * around it — which is where a laundering pattern actually becomes legible.
 */
export default function Timeline({ customerId, onCustomerChange }) {
  const [input, setInput] = useState(customerId || 'CUST_00026');
  const [entries, setEntries] = useState(null);
  const [customer, setCustomer] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => { if (customerId) { setInput(customerId); load(customerId); } }, [customerId]);
  useEffect(() => { load(input); /* initial load */ }, []); // eslint-disable-line

  async function load(id) {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      const [timeline, cust] = await Promise.all([
        api.timeline(id, 100),
        api.customer(id).catch(() => null),
      ]);
      setEntries(timeline);
      setCustomer(cust);
      onCustomerChange?.(id);
    } catch (e) { setError(e.message); setEntries(null); }
    finally { setLoading(false); }
  }

  return (
    <>
      <h2>Customer transaction timeline</h2>
      <p className="sub">
        Most recent activity first. Transactions cited as evidence by an alert are marked in red.
      </p>

      <div className="filters">
        <input type="text" value={input} placeholder="CUST_00026"
               onChange={(e) => setInput(e.target.value.trim().toUpperCase())}
               onKeyDown={(e) => e.key === 'Enter' && load(input)} />
        <button className="primary" onClick={() => load(input)}>Load</button>
        <span className="sub" style={{ margin: 0 }}>
          Try CUST_00026 (structuring), CUST_00078 (layering), CUST_00134 (sanctions)
        </span>
      </div>

      <ErrorBox message={error} />

      {customer && (
        <div className="stats">
          <div className="stat"><div className="value" style={{ fontSize: 15 }}>{customer.nameMasked}</div><div className="label">Customer (masked)</div></div>
          <div className="stat"><div className="value" style={{ fontSize: 15 }}>{customer.riskRating}</div><div className="label">Risk rating</div></div>
          <div className="stat"><div className="value" style={{ fontSize: 15 }}>{customer.kycStatus}</div><div className="label">KYC</div></div>
          <div className="stat"><div className="value" style={{ fontSize: 15 }}>{customer.politicallyExposed ? 'Yes' : 'No'}</div><div className="label">PEP</div></div>
        </div>
      )}

      {loading ? <Loading what="timeline" /> : !entries?.length ? (
        <div className="empty-state">No transactions for this customer.</div>
      ) : (
        <div className="heatmap-wrap">
          <div className="tl">
            {entries.map((t) => (
              <div key={t.transactionId} className={`tl-item ${t.alertRefs?.length ? 'flagged' : ''}`}>
                <div className="tl-row">
                  <span className="tl-when">{when(t.timestamp)}</span>
                  <span className={`tl-amt ${t.direction}`}>
                    {t.direction === 'CREDIT' ? '+' : '−'}{money(t.amountBase)}
                  </span>
                  <span style={{ color: 'var(--ink-secondary)' }}>
                    {t.counterpartyName || '—'}
                    {t.counterpartyCountry ? ` · ${t.counterpartyCountry}` : ''}
                  </span>
                  <span className="mono" style={{ color: 'var(--ink-muted)' }}>
                    {t.channel} · {t.accountId}
                  </span>
                  {t.alertRefs?.length > 0 && (
                    <span className="tl-alerts">⚑ {t.alertRefs.join(', ')}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  );
}
