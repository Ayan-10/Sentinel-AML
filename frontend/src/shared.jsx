import React from 'react';

/** Sequential blue ramp, light -> dark, for continuous magnitude (the heatmap). */
export const SEQUENTIAL = [
  '#cde2fb', '#b7d3f6', '#9ec5f4', '#86b6ef', '#6da7ec', '#5598e7',
  '#3987e5', '#2a78d6', '#256abf', '#1c5cab', '#184f95', '#104281', '#0d366b',
];

/** Risk score (0-100) -> a step on the sequential ramp. */
export function rampColor(score) {
  const idx = Math.min(SEQUENTIAL.length - 1,
    Math.max(0, Math.round((score / 100) * (SEQUENTIAL.length - 1))));
  return SEQUENTIAL[idx];
}

/** White ink once the fill is dark enough to need it. */
export function rampInk(score) {
  return score >= 45 ? '#ffffff' : '#0d366b';
}

const SEVERITY_INK = {
  LOW: '#0ca30c', MEDIUM: '#fab219', HIGH: '#ec835a', CRITICAL: '#d03b3b',
};

/** Status badge: colour plus an explicit text label, never colour alone. */
export function Badge({ value }) {
  if (!value) return <span style={{ color: 'var(--ink-muted)' }}>—</span>;
  return <span className={`badge ${value}`}>{value.replace(/_/g, ' ')}</span>;
}

/** Risk score with a small magnitude bar beside the number. */
export function Score({ value }) {
  return (
    <div className="score">
      <span>{value}</span>
      <span className="track">
        <span className="fill" style={{ width: `${value}%`, background: rampColor(value) }} />
      </span>
    </div>
  );
}

export function severityInk(severity) {
  return SEVERITY_INK[severity] || 'var(--ink-muted)';
}

export const money = (n) =>
  n == null ? '—' : 'INR ' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2 });

export const when = (ts) => (ts ? new Date(ts).toLocaleString('en-GB', { hour12: false }) : '—');

export function Loading({ what = 'data' }) {
  return <div className="empty-state">Loading {what}…</div>;
}

export function ErrorBox({ message }) {
  return message ? <div className="error">{message}</div> : null;
}
