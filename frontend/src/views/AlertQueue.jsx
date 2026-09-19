import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Badge, Score, money, when, Loading, ErrorBox } from '../shared.jsx';

const STATUSES   = ['', 'OPEN', 'IN_REVIEW', 'ESCALATED', 'CLOSED'];
const SEVERITIES = ['', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const RULES = ['', 'CTR_THRESHOLD', 'STRUCTURING', 'RAPID_MOVEMENT',
               'HIGH_RISK_JURISDICTION', 'BEHAVIORAL_DEVIATION', 'ROUND_AMOUNT_PATTERN'];

/**
 * The analyst alert queue — business rules 7 and 8: sorted by risk score
 * descending so the highest-risk work reaches the top, with customer PII masked
 * in the list view.
 */
export default function AlertQueue({ onOpenTimeline }) {
  const [filters, setFilters] = useState({ status: 'OPEN', severity: '', ruleCode: '' });
  const [page, setPage] = useState(null);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    api.alerts(filters)
      .then((p) => { setPage(p); setError(null); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [filters]);

  useEffect(() => {
    if (!selected) { setDetail(null); return; }
    api.alert(selected).then(setDetail).catch((e) => setError(e.message));
  }, [selected]);

  function refresh() { setFilters({ ...filters }); }

  return (
    <>
      <h2>Alert queue</h2>
      <p className="sub">
        Sorted by risk score, highest first. Customer names and account numbers are masked
        in this view.
      </p>
      <ErrorBox message={error} />

      <div className="filters">
        {[['status', STATUSES], ['severity', SEVERITIES], ['ruleCode', RULES]].map(([key, options]) => (
          <select key={key} value={filters[key]} onChange={(e) => setFilters({ ...filters, [key]: e.target.value })}>
            {options.map((o) => (
              <option key={o} value={o}>{o ? o.replace(/_/g, ' ') : `All ${key === 'ruleCode' ? 'rules' : key}`}</option>
            ))}
          </select>
        ))}
        <button className="ghost" onClick={refresh}>Refresh</button>
        {page && <span className="sub" style={{ margin: 0 }}>{page.totalElements.toLocaleString()} alerts</span>}
      </div>

      <div className="split">
        <div>
          {loading ? <Loading what="alerts" /> : !page?.content.length ? (
            <div className="empty-state">No alerts match these filters.</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Alert</th><th>Customer</th><th>Rule</th>
                  <th>Severity</th><th>Status</th><th style={{ textAlign: 'right' }}>Risk</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((a) => (
                  <tr key={a.alertRef}
                      className={selected === a.alertRef ? 'selected' : ''}
                      onClick={() => setSelected(a.alertRef)}>
                    <td className="mono">{a.alertRef}</td>
                    <td>
                      <div>{a.customerNameMasked}</div>
                      <div className="mono" style={{ color: 'var(--ink-muted)' }}>{a.customerId}</div>
                    </td>
                    <td style={{ fontSize: 12.5 }}>{a.ruleCode.replace(/_/g, ' ')}</td>
                    <td><Badge value={a.severity} /></td>
                    <td><Badge value={a.status} /></td>
                    <td className="num"><Score value={a.riskScore} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {detail
          ? <AlertDetail detail={detail} onOpenTimeline={onOpenTimeline} onChanged={() => { refresh(); setSelected(detail.alertRef); }} />
          : <div className="panel"><p className="sub" style={{ margin: 0 }}>Select an alert to see why it fired.</p></div>}
      </div>
    </>
  );
}

/** Alert detail: the explanation, the evidence behind it, and the disposition action. */
function AlertDetail({ detail, onOpenTimeline, onChanged }) {
  const [reason, setReason] = useState('');
  const [disposition, setDisposition] = useState('FALSE_POSITIVE');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  async function close() {
    setBusy(true); setError(null);
    try {
      await api.transitionAlert(detail.alertRef,
        { targetStatus: 'CLOSED', disposition, reason });
      setNotice('Alert closed. Disposition and your identity are retained permanently.');
      setReason('');
      onChanged();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function openCase() {
    setBusy(true); setError(null);
    try {
      const created = await api.openCase({
        customerId: detail.customerId,
        title: `${detail.typology.replace(/_/g, ' ')} — ${detail.customerId}`,
        alertRefs: [detail.alertRef],
        narrative: detail.explanation,
      });
      setNotice(`Case ${created.caseRef} opened. See the Cases tab.`);
      onChanged();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return (
    <aside className="panel">
      <h3>{detail.typology.replace(/_/g, ' ')}</h3>
      <div className="ref">{detail.alertRef}</div>

      <ErrorBox message={error} />
      {notice && <div className="notice" style={{ marginTop: 12 }}>{notice}</div>}

      <section>
        <h4>Why this fired</h4>
        <div className="explanation">{detail.explanation}</div>
      </section>

      <section>
        <h4>Risk score — {detail.riskScore} ({detail.severity})</h4>
        <dl className="kv">
          {Object.entries(detail.scoreBreakdown || {})
            .filter(([k]) => k !== 'total')
            .map(([k, v]) => (
              <React.Fragment key={k}>
                <dt>{k.replace(/([A-Z])/g, ' $1').toLowerCase()}</dt>
                <dd>{v > 0 ? `+${v}` : v}</dd>
              </React.Fragment>
            ))}
        </dl>
      </section>

      <section>
        <h4>Evidence — {detail.evidence?.length || 0} transaction(s)</h4>
        <table>
          <tbody>
            {(detail.evidence || []).map((t) => (
              <tr key={t.transactionId} style={{ cursor: 'default' }}>
                <td className="mono" style={{ padding: '6px 8px' }}>
                  {t.transactionId}
                  <div style={{ color: 'var(--ink-muted)' }}>{when(t.timestamp)}</div>
                </td>
                <td style={{ padding: '6px 8px', textAlign: 'right' }}>
                  <div className="mono">{money(t.amountBase)}</div>
                  <div style={{ color: 'var(--ink-muted)', fontSize: 11.5 }}>
                    {t.direction}{t.counterpartyCountry ? ` · ${t.counterpartyCountry}` : ''}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {detail.disposition ? (
        <section>
          <h4>Disposition</h4>
          <dl className="kv">
            <dt>Outcome</dt><dd><Badge value={detail.disposition} /></dd>
            <dt>By</dt><dd>{detail.disposedBy}</dd>
            <dt>When</dt><dd>{when(detail.disposedAt)}</dd>
            <dt>Reason</dt><dd>{detail.dispositionReason}</dd>
          </dl>
        </section>
      ) : (
        <section>
          <h4>Disposition</h4>
          <select value={disposition} onChange={(e) => setDisposition(e.target.value)} style={{ width: '100%' }}>
            <option value="FALSE_POSITIVE">False positive</option>
            <option value="TRUE_POSITIVE">True positive</option>
            <option value="CLEARED_NO_ACTION">Cleared — no action</option>
            <option value="ESCALATED_TO_SAR">Escalate to SAR (senior only)</option>
          </select>
          <textarea
            placeholder="Reason — required, retained permanently for audit"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            style={{ marginTop: 8 }}
          />
          <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
            <button className="primary" disabled={busy || !reason.trim()} onClick={close}>Close alert</button>
            <button className="ghost" disabled={busy} onClick={openCase}>Open case</button>
          </div>
        </section>
      )}

      <section>
        <button className="ghost" onClick={() => onOpenTimeline(detail.customerId)}>
          View {detail.customerId} timeline →
        </button>
      </section>
    </aside>
  );
}
