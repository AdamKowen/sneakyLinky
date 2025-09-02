import { useState, useEffect } from "react";
import { useToast } from "../../../layouts/AdminLayout/ToastContext";
import { getDomainStats } from "../../../services/domain";

export default function DomainStatsCard({ refreshKey = 0}) {
  const [stats, setStats] = useState({ total: 0, suspicious: 0, safe: 0 });
  const [loading, setLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(null);
  const { error } = useToast();

  const fetchStats = async () => {
    setLoading(true);
    try {
      const data = await getDomainStats();
      // Expected format: { total: 100, suspicious: 25, safe: 75 }
      // Or handle whatever format your API returns
      const totalCount = data.total || data.count || 0;
      const suspiciousCount = data.suspicious || 0;
      const safeCount = data.safe || (totalCount - suspiciousCount);
      
      setStats({ 
        total: totalCount, 
        suspicious: suspiciousCount, 
        safe: safeCount 
      });
      setLastUpdated(new Date());
    } catch (err) {
      const message = err?.response?.data?.error || err.message || "Failed to fetch stats";
      error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchStats(); }, [refreshKey]);
  
  // Fetch on mount and every 5 minutes
  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 5 * 60 * 1000); // 5 minutes
    return () => clearInterval(interval);
  }, []);

  const formatTime = (date) => {
    if (!date) return '';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <section className="card domain-stats-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 className="card-title">Domain Stats</h2>
        {loading && <span style={{ fontSize: '12px', color: '#6b7280' }}>⟳</span>}
      </div>
      <p className="card-sub">GET /v1/domain/stats</p>

      <div className="stats-grid">
        <div className="stat-item">
          <div className="stat-number">{stats.total.toLocaleString()}</div>
          <div className="stat-label">Total </div>
        </div>
        <div className="stat-item">
          <div className="stat-number suspicious-stat">{stats.suspicious.toLocaleString()}</div>
          <div className="stat-label">Suspicious</div>
        </div>
        <div className="stat-item">
          <div className="stat-number safe-stat">{stats.safe.toLocaleString()}</div>
          <div className="stat-label">Safe</div>
        </div>
      </div>
      
      {lastUpdated && (
        <div className="last-updated">
          Last updated: {formatTime(lastUpdated)}
        </div>
      )}
    </section>
  );
}
