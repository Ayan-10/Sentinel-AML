import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import { SEQUENTIAL, rampColor, rampInk, Loading, ErrorBox } from '../shared.jsx';

/**
 * Risk heatmap: customers down, typologies across, cell intensity = the peak
 * risk score that customer reached for that typology.
 *
 * Magnitude is a sequential encoding, so this uses a single hue light -> dark
 * rather than a rainbow: the visual order then matches the numeric order, which
 * a multi-hue scale cannot guarantee. Every cell also prints its score, so the
 * value is never carried by colour alone.
 */
export default function Heatmap({ onOpenTimeline }) {
  const [cells, setCells] = useState(null);
  const [error, setError] = useState(null);
  const [limit, setLimit] = useState(25);

  useEffect(() => {
    api.heatmap().then(setCells).catch((e) => setError(e.message));
  }, []);

  const { typologies, rows } = useMemo(() => {
    if (!cells) return { typologies: [], rows: [] };

    const typologySet = [...new Set(cells.map((c) => c.typology))].sort();

    const byCustomer = new Map();
    for (const c of cells) {
      if (!byCustomer.has(c.customerId)) byCustomer.set(c.customerId, { customerId: c.customerId, cells: {}, peak: 0 });
      const row = byCustomer.get(c.customerId);
      row.cells[c.typology] = c;
      row.peak = Math.max(row.peak, c.peakRiskScore);
    }

    // Riskiest customers first — the analyst's attention belongs at the top.
    const ordered = [...byCustomer.values()].sort((a, b) => b.peak - a.peak).slice(0, limit);
    return { typologies: typologySet, rows: ordered };
  }, [cells, limit]);

  if (error) return <ErrorBox message={error} />;
  if (!cells) return <Loading what="heatmap" />;

  return (
    <>
      <h2>Risk heatmap</h2>
      <p className="sub">
        Peak risk score per customer and typology. Darker means higher risk; the score is
        printed in every cell. Click a row to open that customer's timeline.
      </p>

      <div className="filters">
        <select value={limit} onChange={(e) => setLimit(Number(e.target.value))}>
          {[10, 25, 50, 100].map((n) => <option key={n} value={n}>Top {n} customers</option>)}
        </select>
      </div>

      <div className="heatmap-wrap">
        <table className="heat">
          <thead>
            <tr>
              <th className="row">Customer</th>
              {typologies.map((t) => <th key={t}>{t.replace(/_/g, ' ')}</th>)}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.customerId}>
                <th className="row">
                  <button className="ghost" style={{ padding: '2px 8px', fontSize: 12 }}
                          onClick={() => onOpenTimeline(row.customerId)}>
                    {row.customerId}
                  </button>
                </th>
                {typologies.map((t) => {
                  const cell = row.cells[t];
                  if (!cell) return <td key={t} className="empty" title={`${row.customerId} · ${t}: no alerts`}>–</td>;
                  return (
                    <td key={t}
                        style={{ background: rampColor(cell.peakRiskScore), color: rampInk(cell.peakRiskScore) }}
                        title={`${row.customerId} · ${t.replace(/_/g, ' ')}\nPeak risk ${cell.peakRiskScore} · ${cell.alertCount} alert(s)`}>
                      {cell.peakRiskScore}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>

        <div className="legend">
          <span>Lower risk</span>
          {SEQUENTIAL.filter((_, i) => i % 2 === 0).map((c) => (
            <span key={c} className="swatch" style={{ background: c }} />
          ))}
          <span>Higher risk</span>
          <span style={{ marginLeft: 12 }}>
            <span className="swatch" style={{ background: 'var(--surface-sunken)', display: 'inline-block', verticalAlign: 'middle' }} /> no alerts
          </span>
        </div>
      </div>
    </>
  );
}
