import { useState, useEffect, useRef } from "react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, ReferenceLine, Area, AreaChart
} from "recharts";

// ── Palette ──────────────────────────────────────────────────────────────────
const C = {
  bg:        "#0A0C10",
  surface:   "#111318",
  surfaceHi: "#181C24",
  border:    "#1E2330",
  borderHi:  "#2A3045",
  text:      "#E2E8F0",
  muted:     "#64748B",
  dim:       "#334155",
  green:     "#22D3A4",
  greenDim:  "#0D4F3C",
  red:       "#F43F5E",
  redDim:    "#4C1426",
  amber:     "#F59E0B",
  amberDim:  "#422006",
  blue:      "#38BDF8",
  blueDim:   "#0C2D48",
  purple:    "#A78BFA",
};

// ── Helpers ───────────────────────────────────────────────────────────────────
function makePoint(i, errorRate, latency) {
  return {
    t: i,
    errorRate: Math.max(0, Math.min(100, errorRate + (Math.random() - 0.5) * 3)),
    latency:   Math.max(0, latency + (Math.random() - 0.5) * 20),
    anomaly:   0,
  };
}

function now() {
  return new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

// ── Sub-components ─────────────────────────────────────────────────────────────
function StatCard({ label, value, unit, color, sub }) {
  return (
    <div style={{
      background: C.surface,
      border: `1px solid ${C.border}`,
      borderRadius: 12,
      padding: "16px 20px",
      display: "flex",
      flexDirection: "column",
      gap: 4,
    }}>
      <span style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", color: C.muted, fontFamily: "monospace" }}>{label}</span>
      <span style={{ fontSize: 28, fontWeight: 700, color: color || C.text, fontVariantNumeric: "tabular-nums", lineHeight: 1.1 }}>
        {value}<span style={{ fontSize: 14, color: C.muted, marginLeft: 3 }}>{unit}</span>
      </span>
      {sub && <span style={{ fontSize: 11, color: C.muted }}>{sub}</span>}
    </div>
  );
}

function CircuitLight({ state }) {
  // state: "CLOSED" | "OPEN" | "HALF_OPEN"
  const cfg = {
    CLOSED:    { color: C.green,  label: "CLOSED",    sub: "Healthy — traffic flowing" },
    OPEN:      { color: C.red,    label: "OPEN",       sub: "Circuit tripped — traffic blocked" },
    HALF_OPEN: { color: C.amber,  label: "HALF-OPEN",  sub: "Probing — test requests only" },
  }[state];
  return (
    <div style={{
      background: C.surface,
      border: `1px solid ${C.border}`,
      borderLeft: `3px solid ${cfg.color}`,
      borderRadius: 12,
      padding: "14px 20px",
      display: "flex",
      alignItems: "center",
      gap: 14,
    }}>
      <div style={{
        width: 18, height: 18, borderRadius: "50%",
        background: cfg.color,
        boxShadow: `0 0 12px ${cfg.color}99`,
        flexShrink: 0,
        animation: state === "OPEN" ? "pulse 1s ease-in-out infinite" : "none",
      }} />
      <div>
        <div style={{ fontSize: 11, color: C.muted, letterSpacing: "0.08em", textTransform: "uppercase", fontFamily: "monospace" }}>Circuit Breaker</div>
        <div style={{ fontSize: 16, fontWeight: 700, color: cfg.color }}>{cfg.label}</div>
        <div style={{ fontSize: 11, color: C.muted }}>{cfg.sub}</div>
      </div>
    </div>
  );
}

function AnomalyScore({ score }) {
  const pct = Math.round(score * 100);
  const color = score > 0.75 ? C.red : score > 0.45 ? C.amber : C.green;
  return (
    <div style={{
      background: C.surface,
      border: `1px solid ${C.border}`,
      borderRadius: 12,
      padding: "16px 20px",
    }}>
      <div style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", color: C.muted, fontFamily: "monospace", marginBottom: 8 }}>AI Anomaly Score</div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 6, marginBottom: 10 }}>
        <span style={{ fontSize: 28, fontWeight: 700, color, fontVariantNumeric: "tabular-nums" }}>{pct}</span>
        <span style={{ fontSize: 14, color: C.muted }}>/ 100</span>
      </div>
      <div style={{ height: 6, background: C.border, borderRadius: 99, overflow: "hidden" }}>
        <div style={{
          height: "100%",
          width: `${pct}%`,
          background: `linear-gradient(90deg, ${C.green}, ${color})`,
          borderRadius: 99,
          transition: "width 0.6s ease, background 0.6s ease",
        }} />
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", marginTop: 5 }}>
        <span style={{ fontSize: 10, color: C.dim }}>Normal</span>
        <span style={{ fontSize: 10, color: C.dim }}>Threshold 75</span>
        <span style={{ fontSize: 10, color: C.dim }}>Critical</span>
      </div>
    </div>
  );
}

function EventLog({ events }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.scrollTop = ref.current.scrollHeight; }, [events]);
  const iconFor = (type) => ({ anomaly: "⚠", heal: "✓", fail: "✕", info: "·" }[type] || "·");
  const colorFor = (type) => ({ anomaly: C.amber, heal: C.green, fail: C.red, info: C.muted }[type] || C.muted);
  return (
    <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 12, padding: "16px 20px", display: "flex", flexDirection: "column", gap: 10 }}>
      <div style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", color: C.muted, fontFamily: "monospace" }}>Event Log</div>
      <div ref={ref} style={{ overflowY: "auto", maxHeight: 160, display: "flex", flexDirection: "column", gap: 4 }}>
        {events.length === 0 && <span style={{ fontSize: 12, color: C.dim }}>No events yet. Monitoring…</span>}
        {events.map((e, i) => (
          <div key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: 12 }}>
            <span style={{ color: colorFor(e.type), fontFamily: "monospace", flexShrink: 0, fontSize: 13 }}>{iconFor(e.type)}</span>
            <span style={{ color: C.muted, flexShrink: 0, fontFamily: "monospace" }}>{e.time}</span>
            <span style={{ color: e.type === "info" ? C.muted : C.text }}>{e.msg}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: C.surfaceHi, border: `1px solid ${C.borderHi}`, borderRadius: 8, padding: "10px 14px", fontSize: 12, color: C.text }}>
      <div style={{ color: C.muted, marginBottom: 6, fontFamily: "monospace" }}>t={label}</div>
      {payload.map((p) => (
        <div key={p.dataKey} style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <div style={{ width: 8, height: 8, borderRadius: 2, background: p.color }} />
          <span style={{ color: C.muted }}>{p.name}:</span>
          <span style={{ fontWeight: 600 }}>{typeof p.value === "number" ? p.value.toFixed(1) : p.value}</span>
        </div>
      ))}
    </div>
  );
};

// ── Main Dashboard ────────────────────────────────────────────────────────────
export default function AIPredictorDashboard() {
  const HISTORY = 40;
  const ANOMALY_THRESHOLD = 0.75;

  const [data, setData] = useState(() =>
    Array.from({ length: HISTORY }, (_, i) => makePoint(i, 2, 120))
  );
  const [failing, setFailing] = useState(false);
  const [circuit, setCircuit] = useState("CLOSED"); // CLOSED | OPEN | HALF_OPEN
  const [anomaly, setAnomaly] = useState(0);
  const [events, setEvents] = useState([{ type: "info", time: now(), msg: "System online. Monitoring Order Service." }]);
  const [healCountdown, setHealCountdown] = useState(0);

  const tick = useRef(0);
  const failingRef = useRef(false);
  const circuitRef = useRef("CLOSED");

  failingRef.current = failing;
  circuitRef.current = circuit;

  function addEvent(type, msg) {
    setEvents((prev) => [...prev.slice(-49), { type, time: now(), msg }]);
  }

  // Simulate AI anomaly score from error rate
  function computeAnomaly(errorRate) {
    // Isolation Forest simplified: maps error rate non-linearly
    if (errorRate < 5) return Math.random() * 0.1;
    if (errorRate < 15) return 0.2 + Math.random() * 0.2;
    if (errorRate < 35) return 0.5 + Math.random() * 0.2;
    return 0.8 + Math.random() * 0.18;
  }

  useEffect(() => {
    const interval = setInterval(() => {
      tick.current += 1;
      const t = tick.current;

      const isFailing = failingRef.current;
      const isOpen = circuitRef.current === "OPEN";

      // Simulate metrics
      const targetErr = isFailing ? 55 + Math.random() * 20 : 2;
      const targetLat = isFailing ? 800 + Math.random() * 300 : 120;
      const errorRate = isFailing ? targetErr : targetErr;
      const latency   = isFailing ? targetLat : targetLat;

      const score = isOpen ? computeAnomaly(2) : computeAnomaly(errorRate);
      setAnomaly(score);

      setData((prev) => {
        const next = [...prev.slice(-HISTORY + 1), makePoint(t, errorRate, latency)];
        return next;
      });

      // AI triggers circuit breaker
      if (score > ANOMALY_THRESHOLD && circuitRef.current === "CLOSED") {
        setCircuit("OPEN");
        circuitRef.current = "OPEN";
        addEvent("anomaly", `Anomaly score ${Math.round(score * 100)} > 75 — circuit breaker OPENED. Traffic blocked.`);
        // Auto heal after 10s
        setHealCountdown(10);
      }
    }, 800);
    return () => clearInterval(interval);
  }, []);

  // Heal countdown
  useEffect(() => {
    if (healCountdown <= 0) return;
    const t = setTimeout(() => {
      setHealCountdown((c) => {
        if (c === 1) {
          setCircuit("HALF_OPEN");
          circuitRef.current = "HALF_OPEN";
          addEvent("heal", "Entering HALF-OPEN state — probing with test requests.");
          setTimeout(() => {
            if (!failingRef.current) {
              setCircuit("CLOSED");
              circuitRef.current = "CLOSED";
              addEvent("heal", "Service healthy — circuit CLOSED. Normal traffic resumed.");
            }
          }, 4000);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
    return () => clearTimeout(t);
  }, [healCountdown]);

  function triggerFailure() {
    if (failing) return;
    setFailing(true);
    failingRef.current = true;
    addEvent("fail", "POST /fail called — Order Service now returning HTTP 500 errors.");
  }

  function triggerHeal() {
    setFailing(false);
    failingRef.current = false;
    if (circuit === "OPEN") {
      setCircuit("HALF_OPEN");
      circuitRef.current = "HALF_OPEN";
      addEvent("heal", "Manual heal triggered. Entering HALF-OPEN state.");
      setTimeout(() => {
        setCircuit("CLOSED");
        circuitRef.current = "CLOSED";
        addEvent("heal", "Service confirmed healthy — circuit CLOSED.");
      }, 3000);
    } else {
      addEvent("info", "Manual heal triggered — failure mode cleared.");
    }
    setHealCountdown(0);
  }

  const latestErr = data[data.length - 1]?.errorRate ?? 0;
  const latestLat = data[data.length - 1]?.latency ?? 0;
  const totalRequests = tick.current * 12;

  return (
    <div style={{
      minHeight: "100vh",
      background: C.bg,
      color: C.text,
      fontFamily: "'IBM Plex Mono', 'Fira Code', monospace",
      padding: "24px",
      boxSizing: "border-box",
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;700&family=IBM+Plex+Sans:wght@400;500;600&display=swap');
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
        @keyframes slideIn { from{opacity:0;transform:translateY(-8px)} to{opacity:1;transform:translateY(0)} }
        ::-webkit-scrollbar { width: 4px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: ${C.border}; border-radius: 99px; }
      `}</style>

      {/* Header */}
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 28, flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ fontSize: 10, letterSpacing: "0.2em", color: C.muted, textTransform: "uppercase", marginBottom: 4 }}>Persistent Semicolon Hackathon · Order Service</div>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: C.text, fontFamily: "'IBM Plex Sans', sans-serif", letterSpacing: "-0.02em" }}>
            AI Failure Prediction
          </h1>
          <div style={{ fontSize: 11, color: C.muted, marginTop: 2 }}>
            Isolation Forest · Self-Healing · Real-time
          </div>
        </div>

        {/* Action buttons */}
        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
          {healCountdown > 0 && (
            <div style={{ fontSize: 11, color: C.amber, background: C.amberDim, border: `1px solid ${C.amber}44`, borderRadius: 8, padding: "6px 12px", animation: "pulse 1s ease-in-out infinite" }}>
              Auto-heal in {healCountdown}s
            </div>
          )}
          <button
            onClick={triggerHeal}
            style={{
              background: "transparent",
              border: `1px solid ${C.green}`,
              color: C.green,
              borderRadius: 8,
              padding: "8px 16px",
              fontSize: 12,
              fontFamily: "inherit",
              cursor: "pointer",
              letterSpacing: "0.05em",
              transition: "background 0.2s",
            }}
            onMouseEnter={e => e.target.style.background = C.greenDim}
            onMouseLeave={e => e.target.style.background = "transparent"}
          >
            ✓ Heal Service
          </button>
          <button
            onClick={triggerFailure}
            disabled={failing}
            style={{
              background: failing ? C.redDim : "transparent",
              border: `1px solid ${failing ? C.red + "88" : C.red}`,
              color: failing ? C.red + "88" : C.red,
              borderRadius: 8,
              padding: "8px 16px",
              fontSize: 12,
              fontFamily: "inherit",
              cursor: failing ? "not-allowed" : "pointer",
              letterSpacing: "0.05em",
              transition: "background 0.2s",
              display: "flex",
              alignItems: "center",
              gap: 6,
            }}
            onMouseEnter={e => { if (!failing) e.currentTarget.style.background = C.redDim; }}
            onMouseLeave={e => { if (!failing) e.currentTarget.style.background = "transparent"; }}
          >
            {failing ? (
              <><span style={{ width: 8, height: 8, borderRadius: "50%", background: C.red, display: "inline-block", animation: "pulse 0.8s ease-in-out infinite" }} /> Failing…</>
            ) : "✕ Trigger Failure"}
          </button>
        </div>
      </div>

      {/* Top stat row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12, marginBottom: 16 }}>
        <StatCard
          label="Error Rate"
          value={latestErr.toFixed(1)}
          unit="%"
          color={latestErr > 30 ? C.red : latestErr > 10 ? C.amber : C.green}
          sub={failing ? "⚠ Failure mode active" : "Normal"}
        />
        <StatCard
          label="Avg Latency"
          value={Math.round(latestLat)}
          unit="ms"
          color={latestLat > 500 ? C.red : latestLat > 250 ? C.amber : C.green}
          sub="p95 estimate"
        />
        <StatCard
          label="Requests"
          value={totalRequests.toLocaleString()}
          unit=""
          color={C.blue}
          sub="simulated total"
        />
        <AnomalyScore score={anomaly} />
      </div>

      {/* Circuit breaker */}
      <div style={{ marginBottom: 16 }}>
        <CircuitLight state={circuit} />
      </div>

      {/* Charts */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 16 }}>
        {/* Error rate chart */}
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 12, padding: "16px 20px" }}>
          <div style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", color: C.muted, marginBottom: 12 }}>Error Rate %</div>
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
              <defs>
                <linearGradient id="errGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={C.red} stopOpacity={0.25} />
                  <stop offset="95%" stopColor={C.red} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={C.border} vertical={false} />
              <XAxis dataKey="t" hide />
              <YAxis domain={[0, 100]} tick={{ fill: C.muted, fontSize: 10 }} />
              <Tooltip content={<CustomTooltip />} />
              <ReferenceLine y={30} stroke={C.amber} strokeDasharray="4 4" strokeWidth={1} />
              <Area type="monotone" dataKey="errorRate" name="Error %" stroke={C.red} fill="url(#errGrad)" strokeWidth={2} dot={false} isAnimationActive={false} />
            </AreaChart>
          </ResponsiveContainer>
          <div style={{ fontSize: 10, color: C.dim, marginTop: 6 }}>
            <span style={{ color: C.amber }}>──</span> 30% warn threshold
          </div>
        </div>

        {/* Latency chart */}
        <div style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 12, padding: "16px 20px" }}>
          <div style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase", color: C.muted, marginBottom: 12 }}>Latency (ms)</div>
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
              <defs>
                <linearGradient id="latGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={C.blue} stopOpacity={0.25} />
                  <stop offset="95%" stopColor={C.blue} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={C.border} vertical={false} />
              <XAxis dataKey="t" hide />
              <YAxis tick={{ fill: C.muted, fontSize: 10 }} />
              <Tooltip content={<CustomTooltip />} />
              <ReferenceLine y={400} stroke={C.amber} strokeDasharray="4 4" strokeWidth={1} />
              <Area type="monotone" dataKey="latency" name="Latency ms" stroke={C.blue} fill="url(#latGrad)" strokeWidth={2} dot={false} isAnimationActive={false} />
            </AreaChart>
          </ResponsiveContainer>
          <div style={{ fontSize: 10, color: C.dim, marginTop: 6 }}>
            <span style={{ color: C.amber }}>──</span> 400ms warn threshold
          </div>
        </div>
      </div>

      {/* Event log */}
      <EventLog events={events} />

      {/* Footer */}
      <div style={{ marginTop: 16, display: "flex", justifyContent: "space-between", fontSize: 10, color: C.dim }}>
        <span>AI model: Ollama - Llama 3</span>
        <span>Polling: 800ms · Threshold: 75/100</span>
        <span style={{ color: circuit === "CLOSED" ? C.green : C.red }}>
          {circuit === "CLOSED" ? "● HEALTHY" : circuit === "OPEN" ? "● DEGRADED" : "● PROBING"}
        </span>
      </div>
    </div>
  );
}