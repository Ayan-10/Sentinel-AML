import React, { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Loading, ErrorBox } from '../shared.jsx';

/** Single hue for single-measure magnitude — colour carries no extra meaning here. */
const BAR = '#256abf';
const BAR_MUTED = '#9ec5f4';

/**
 * Analyst productivity — how well the programme is being worked, as distinct
 * from what it has found.
 */
export default function Productivity() {
  const [m, setM] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.productivity().then(setM).catch((e) => setError(e.message));
  }, []);

  if (error) return <ErrorBox message={error} />;
  if (!m) return <Loading what="metrics" />;

  const pct = (v) => (v == null ? '—' : `${v}%`);
  const hrs = (v) => (v == null ? '—' : v < 48 ? `${v} h` : `${(v / 24).toFixed(1)} d`);

  return (
    <>
      <h2>Analyst productivity</h2>
      <p className="sub">
        Operational health of the monitoring programme. A rate over zero disposed alerts is
        shown as “—”, not 0% — unknown is not the same as perfect.
      </p>

      <div className="stats">
        <div className="stat">
          <div className="value">{pct(m.falsePositiveRatePct)}</div>
          <div className="label">False-positive rate</div>
        </div>
        <div className="stat">
          <div className="value">{hrs(m.medianHoursToDisposition)}</div>
          <div className="label">Median time to disposition</div>
        </div>
        <div className="stat">
          <div className="value">{hrs(m.avgHoursToDisposition)}</div>
          <div className="label">Mean time to disposition</div>
        </div>
        <div className="stat">
          <div className="value">{m.disposedAlerts.toLocaleString()}</div>
          <div className="label">Disposed</div>
        </div>
        <div className="stat">
          <div className="value">{m.openAlerts.toLocaleString()}</div>
          <div className="label">Still open</div>
        </div>
        <div className="stat">
          <div className="value">{m.escalatedToSar.toLocaleString()}</div>
          <div className="label">Escalated to SAR</div>
        </div>
      </div>

      {m.avgHoursToDisposition != null
        && m.avgHoursToDisposition > m.medianHoursToDisposition * 1.5 && (
        <p className="sub">
          The mean sits well above the median, so a minority of long-running investigations is
          stretching the average — the median is the better read on a typical alert.
        </p>
      )}

      <div className="split" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <RuleQuality rows={m.falsePositiveRateByRule} />
        <VolumeTrend rows={m.alertVolumeTrend} />
      </div>

      <div className="heatmap-wrap" style={{ marginTop: 16 }}>
        <h3 style={{ marginTop: 0, fontSize: 15 }}>Disposition breakdown</h3>
        <table>
          <tbody>
            {Object.entries(m.dispositionBreakdown).map(([k, v]) => (
              <tr key={k} style={{ cursor: 'default' }}>
                <td>{k.replace(/_/g, ' ')}</td>
                <td className="num">{v.toLocaleString()}</td>
                <td style={{ width: '60%' }}>
                  <span className="track" style={{ width: '100%', display: 'block' }}>
                    <span className="fill" style={{
                      width: m.disposedAlerts ? `${(v / m.disposedAlerts) * 100}%` : 0,
                      background: BAR,
                    }} />
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

/**
 * False-positive rate per rule — the figure that names which control to retune,
 * which is what makes this actionable rather than merely informative.
 */
function RuleQuality({ rows }) {
  if (!rows?.length) {
    return (
      <div className="heatmap-wrap">
        <h3 style={{ marginTop: 0, fontSize: 15 }}>False-positive rate by rule</h3>
        <p className="sub" style={{ margin: 0 }}>No alerts disposed yet.</p>
      </div>
    );
  }
  return (
    <div className="heatmap-wrap">
      <h3 style={{ marginTop: 0, fontSize: 15 }}>False-positive rate by rule</h3>
      <p className="sub">
        Noisiest first. A high rate means the rule is generating work, not findings. Rules with
        fewer than 20 disposed alerts report no rate — a percentage over a handful of cases would
        rank a precise rule as though it were a noisy one.
      </p>
      <table>
        <thead>
          <tr><th>Rule</th><th style={{ textAlign: 'right' }}>Disposed</th><th>False-positive rate</th></tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.ruleCode} style={{ cursor: 'default' }}>
              <td style={{ fontSize: 12.5 }}>{r.ruleCode.replace(/_/g, ' ')}</td>
              <td className="num">{r.disposed}</td>
              <td>
                {r.sufficientSample ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span className="track" style={{ flex: 1 }}>
                      <span className="fill" style={{
                        width: `${r.falsePositiveRatePct}%`,
                        background: r.falsePositiveRatePct >= 70 ? BAR : BAR_MUTED,
                      }} />
                    </span>
                    <span className="mono" style={{ minWidth: 48, textAlign: 'right' }}>
                      {r.falsePositiveRatePct}%
                    </span>
                  </div>
                ) : (
                  <span className="sub" style={{ margin: 0 }}>
                    sample too small (n={r.disposed}) — no rate reported
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Alerts detected per day. A single series, so no legend — the heading names it.
 * Only the peak is labelled; a number on every bar would be noise.
 */
function VolumeTrend({ rows }) {
  if (!rows?.length) {
    return (
      <div className="heatmap-wrap">
        <h3 style={{ marginTop: 0, fontSize: 15 }}>Alert volume</h3>
        <p className="sub" style={{ margin: 0 }}>No detections in this window.</p>
      </div>
    );
  }

  const W = 520, H = 180, PAD_L = 34, PAD_B = 26, PAD_T = 14;
  const max = Math.max(...rows.map((r) => r.alertCount));
  const plotW = W - PAD_L - 8;
  const plotH = H - PAD_B - PAD_T;
  const slot = plotW / rows.length;
  const barW = Math.max(1, slot - 2);          // 2px surface gap between bars
  const peak = rows.reduce((a, b) => (b.alertCount > a.alertCount ? b : a), rows[0]);

  return (
    <div className="heatmap-wrap">
      <h3 style={{ marginTop: 0, fontSize: 15 }}>Alerts detected per day</h3>
      <p className="sub">{rows[0].day} to {rows[rows.length - 1].day}</p>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img"
           aria-label={`Alerts detected per day, peak ${peak.alertCount} on ${peak.day}`}>
        {[0, 0.5, 1].map((f) => {
          const y = PAD_T + plotH - f * plotH;
          return (
            <g key={f}>
              <line x1={PAD_L} x2={W - 8} y1={y} y2={y} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD_L - 6} y={y + 3} textAnchor="end"
                    fontSize="10" fill="var(--ink-muted)">{Math.round(max * f)}</text>
            </g>
          );
        })}
        {rows.map((r, i) => {
          const h = max ? (r.alertCount / max) * plotH : 0;
          return (
            <rect key={r.day} x={PAD_L + i * slot} y={PAD_T + plotH - h}
                  width={barW} height={h} rx="2" fill={BAR}>
              <title>{`${r.day}: ${r.alertCount} alert(s)`}</title>
            </rect>
          );
        })}
        <text x={PAD_L} y={H - 8} fontSize="10" fill="var(--ink-muted)">{rows[0].day}</text>
        <text x={W - 8} y={H - 8} fontSize="10" fill="var(--ink-muted)" textAnchor="end">
          {rows[rows.length - 1].day}
        </text>
      </svg>
      <div className="legend">Peak {peak.alertCount} alerts on {peak.day}</div>
    </div>
  );
}
