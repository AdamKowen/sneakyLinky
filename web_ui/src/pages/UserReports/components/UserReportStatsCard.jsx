import { useState, useEffect } from 'react';
import { getUserReportsStats } from '../../../services/userReports';
import { FiBarChart2, FiClipboard, FiCheckCircle, FiClock, FiAlertTriangle } from 'react-icons/fi';

export default function UserReportStatsCard({ refreshKey = 0 }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchStats = async () => {
    try {
      setLoading(true);
      const statsData = await getUserReportsStats();
      setStats(statsData);
      setError(null);
    } catch (err) {
      console.error('Error fetching stats:', err);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  // Refetch when refreshKey changes
  useEffect(() => {
    if (refreshKey !== 0) {
      fetchStats();
    }
  }, [refreshKey]);

  if (loading) {
    return (
      <div className="stats-card">
        <div className="stats-card-loading">
          <div className="loading-spinner"></div>
          <span>Loading statistics...</span>
        </div>
      </div>
    );
  }

  if (error || !stats) {
    return (
      <div className="stats-card error">
        <div className="stats-card-error">
          <FiAlertTriangle style={{ marginRight: '8px' }} />
          <span>Failed to load statistics</span>
        </div>
      </div>
    );
  }

  const processedPercentage = stats?.total > 0 ? Math.round((stats.classified / stats.total) * 100) : 0;

  // Fallback data for testing
  const displayStats = stats || { total: 0, classified: 0, unclassified: 0 };

  return (
    <div className="stats-card">
      <div className="stats-card-header">
        <h2><FiBarChart2 style={{ marginRight: '8px' }} />Reports Overview</h2>
        <div className="stats-card-progress">
          <div className="progress-bar">
            <div 
              className="progress-fill" 
              style={{ width: `${processedPercentage}%` }}
            ></div>
          </div>
          <span className="progress-text">{processedPercentage}% Processed</span>
        </div>
      </div>
      
      <div className="stats-grid">
        <div className="stat-item total">
          <div className="stat-icon"><FiClipboard /></div>
          <div className="stat-content">
            <div className="stat-number">{displayStats.total}</div>
            <div className="stat-label">Total Reports</div>
          </div>
        </div>
        
        <div className="stat-item processed">
          <div className="stat-icon"><FiCheckCircle /></div>
          <div className="stat-content">
            <div className="stat-number">{displayStats.classified}</div>
            <div className="stat-label">Processed</div>
          </div>
        </div>
        
        <div className="stat-item pending">
          <div className="stat-icon"><FiClock /></div>
          <div className="stat-content">
            <div className="stat-number">{displayStats.unclassified}</div>
            <div className="stat-label">Pending Review</div>
          </div>
        </div>
      </div>
    </div>
  );
}
