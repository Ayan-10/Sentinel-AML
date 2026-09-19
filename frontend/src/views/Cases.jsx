import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Badge, Score, when, Loading, ErrorBox } from '../shared.jsx';

/**
 * Case queue and case detail — the analyst workflow end of the system.
 * The detail view shows member alerts alongside the immutable audit trail, so
 * every state change is attributable on the same screen as the decision.
 */
export default function Cases() {
  const [page, setPage] = useState(null);
  const [selected, setSelected] = useState(null);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState('');

  function refresh() {
    api.cases(status ? { status } : {})
      .then(setPage)
      .catch((e) => setError(e.message));
  }

  useEffect(refresh, [status]);

  useEffect(() => {
    if (!selected) { setDetail(null); return; }
    api.caseDetail(selected).then(setDetail).catch((e) => setError(e.message));
  }, [selected]);

  return (
    <>
      <h2>Cases</h2>
      <p className="sub">Investigations grouping one or more alerts for a single customer.</p>
      <ErrorBox message={error} />

      <div className="filters">
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          {['', 'NEW', 'INVESTIGATING', 'PENDING_APPROVAL', 'CLOSED'].map((s) => (
            <option key={s} value={s}>{s ? s.replace(/_/g, ' ') : 'All statuses'}</option>
          ))}
        </select>
        <button className="ghost" onClick={refresh}>Refresh</button>
        {page && <span className="sub" style={{ margin: 0 }}>{page.totalElements} cases</span>}
      </div>

      <div className="split">
        <div>
          {!page ? <Loading what="cases" /> : !page.content.length ? (
            <div className="empty-state">
              No cases yet. Open one from an alert in the Alert queue.
            </div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Case</th><th>Customer</th><th>Status</th>
                  <th>Priority</th><th>Assigned</th><th style={{ textAlign: 'right' }}>Risk</th>
                </tr>
              </thead>
              <tbody>
                {page.content.map((c) => (
                  <tr key={c.caseRef}
                      className={selected === c.caseRef ? 'selected' : ''}
                      onClick={() => setSelected(c.caseRef)}>
                    <td className="mono">{c.caseRef}</td>
                    <td className="mono">{c.customerId}</td>
                    <td><Badge value={c.status} /></td>
                    <td><Badge value={c.priority} /></td>
                    <td style={{ fontSize: 12.5 }}>{c.assignedTo || '—'}</td>
                    <td className="num"><Score value={c.aggregateRiskScore} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {detail
          ? <CaseDetail detail={detail} onChanged={() => { refresh(); api.caseDetail(detail.caseRef).then(setDetail); }} />
          : <div className="panel"><p className="sub" style={{ margin: 0 }}>Select a case to see its alerts and audit trail.</p></div>}
      </div>
    </>
  );
}

/**
 * Draft Suspicious Activity Report for the case.
 *
 * Restricted to SENIOR_ANALYST at the API. An analyst clicking this gets a 403
 * and sees it — which is the point: the console is not what enforces access.
 */
function SarDraftSection({ caseRef }) {
  const [draft, setDraft] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function generate() {
    setBusy(true); setError(null); setDraft(null);
    try {
      setDraft(await api.sarDraft(caseRef));
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h4>Suspicious Activity Report</h4>
      <button className="ghost" disabled={busy} onClick={generate}>
        {busy ? 'Generating…' : 'Generate SAR draft'}
      </button>
      {error && <div className="error" style={{ marginTop: 8 }}>{error}</div>}

      {draft && (
        <div style={{ marginTop: 10 }}>
          <div className="ref" style={{ marginBottom: 6 }}>
            {draft.draftRef} · prepared by {draft.generatedBy}
          </div>

          <dl className="kv">
            <dt>Subject</dt><dd>{draft.subject.fullName} ({draft.subject.nationalId})</dd>
            <dt>Typologies</dt><dd>{draft.typologies.join(', ').replace(/_/g, ' ')}</dd>
            <dt>Transactions</dt><dd>{draft.activity.transactionCount}</dd>
            <dt>Total value</dt>
            <dd>{draft.activity.baseCurrency} {Number(draft.activity.totalValueBase).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</dd>
          </dl>

          <h4 style={{ marginTop: 12 }}>Narrative</h4>
          <div className="explanation" style={{ whiteSpace: 'pre-wrap', maxHeight: 260, overflowY: 'auto' }}>
            {draft.narrative}
          </div>

          <h4 style={{ marginTop: 12 }}>Recommended action</h4>
          <div className="explanation">{draft.recommendedAction}</div>

          <button className="ghost" style={{ marginTop: 10 }}
                  onClick={() => navigator.clipboard?.writeText(draft.narrative)}>
            Copy narrative
          </button>
          <a className="ghost" style={{ marginTop: 10, marginLeft: 8, display: 'inline-block',
                                        textDecoration: 'none', color: 'inherit' }}
             href={`/api/v1/cases/${caseRef}/sar-draft/text`} target="_blank" rel="noreferrer">
            Open plain text ↗
          </a>
        </div>
      )}
    </section>
  );
}

function CaseDetail({ detail, onChanged }) {
  const [reason, setReason] = useState('');
  const [disposition, setDisposition] = useState('TRUE_POSITIVE');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const closed = detail.status === 'CLOSED';

  async function transition(targetStatus, withDisposition = false) {
    setBusy(true); setError(null);
    try {
      await api.transitionCase(detail.caseRef, withDisposition
        ? { targetStatus, disposition, reason }
        : { targetStatus });
      setReason('');
      onChanged();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  return (
    <aside className="panel">
      <h3>{detail.title}</h3>
      <div className="ref">{detail.caseRef}</div>
      <ErrorBox message={error} />

      <section>
        <dl className="kv">
          <dt>Status</dt><dd><Badge value={detail.status} /></dd>
          <dt>Priority</dt><dd><Badge value={detail.priority} /></dd>
          <dt>Customer</dt><dd className="mono">{detail.customerId} · {detail.customerNameMasked}</dd>
          <dt>Aggregate risk</dt><dd>{detail.aggregateRiskScore}</dd>
          <dt>Assigned</dt><dd>{detail.assignedTo || '—'}</dd>
          <dt>Opened</dt><dd>{detail.openedBy} · {when(detail.openedAt)}</dd>
        </dl>
      </section>

      {detail.narrative && (
        <section>
          <h4>Narrative</h4>
          <div className="explanation">{detail.narrative}</div>
        </section>
      )}

      <section>
        <h4>Alerts in this case — {detail.alerts?.length || 0}</h4>
        <table>
          <tbody>
            {(detail.alerts || []).map((a) => (
              <tr key={a.alertRef} style={{ cursor: 'default' }}>
                <td style={{ padding: '6px 8px' }}>
                  <div className="mono" style={{ fontSize: 12 }}>{a.alertRef}</div>
                  <div style={{ fontSize: 12, color: 'var(--ink-muted)' }}>
                    {a.ruleCode.replace(/_/g, ' ')}
                  </div>
                </td>
                <td style={{ padding: '6px 8px', textAlign: 'right' }}>
                  <Badge value={a.severity} />
                  <div style={{ fontSize: 12, marginTop: 2 }}>score {a.riskScore}</div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {closed ? (
        <section>
          <h4>Disposition</h4>
          <dl className="kv">
            <dt>Outcome</dt><dd><Badge value={detail.disposition} /></dd>
            <dt>Reason</dt><dd>{detail.dispositionReason}</dd>
            <dt>Closed</dt><dd>{when(detail.closedAt)}</dd>
          </dl>
        </section>
      ) : (
        <section>
          <h4>Actions</h4>
          {detail.status === 'NEW' && (
            <button className="ghost" disabled={busy}
                    onClick={() => transition('INVESTIGATING')}>Start investigating</button>
          )}
          <select value={disposition} onChange={(e) => setDisposition(e.target.value)}
                  style={{ width: '100%', marginTop: 8 }}>
            <option value="TRUE_POSITIVE">True positive</option>
            <option value="FALSE_POSITIVE">False positive</option>
            <option value="CLEARED_NO_ACTION">Cleared — no action</option>
            <option value="ESCALATED_TO_SAR">Escalate to SAR (senior only)</option>
          </select>
          <textarea placeholder="Reason — required to close, retained permanently"
                    value={reason} onChange={(e) => setReason(e.target.value)}
                    style={{ marginTop: 8 }} />
          <button className="primary" style={{ marginTop: 8 }}
                  disabled={busy || !reason.trim()}
                  onClick={() => transition('CLOSED', true)}>
            Close case
          </button>
          <p className="sub" style={{ marginTop: 8, marginBottom: 0, fontSize: 12 }}>
            Closing cascades the disposition onto every alert above. Nothing is deleted.
          </p>
        </section>
      )}

      <SarDraftSection caseRef={detail.caseRef} />

      <section>
        <h4>Audit trail</h4>
        {!detail.auditTrail?.length ? <p className="sub" style={{ margin: 0 }}>No entries.</p> : (
          <div style={{ fontSize: 12.5 }}>
            {detail.auditTrail.map((e, i) => (
              <div key={i} style={{ padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                <div className="mono">{e.action.replace(/_/g, ' ')}</div>
                <div style={{ color: 'var(--ink-muted)' }}>
                  {e.actor}
                  {e.fromState || e.toState ? ` · ${e.fromState || '—'} → ${e.toState || '—'}` : ''}
                  {' · '}{when(e.occurredAt)}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </aside>
  );
}
