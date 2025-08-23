import { useState } from 'react';
import { updateAdminDecision } from '../../../services/userReports';
import { updateDomainSuspicious /*, createDomain */ } from '../../../services/domain'; // TODO: Enable when you also want to update the domains table

export default function UserReportCard({ report, onUpdate }) {
  const [adminDecision, setAdminDecision] = useState(
    report.adminDecision !== null && report.adminDecision !== undefined 
      ? report.adminDecision.toString() 
      : ''
  );
  const [showFullReason, setShowFullReason] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [isRemoving, setIsRemoving] = useState(false);
  
  const handleConfirmDecision = async () => {
    if (!adminDecision) return;
    
    try {
      setUpdating(true);
      const decisionValue = parseInt(adminDecision);
      await updateAdminDecision(report.id, decisionValue);
      console.log(`Admin decision for report ${report.id}: ${decisionValue}`);
      
      // TODO: Domain table update (disabled for now).
      try {
        const hostname = new URL(report.url).hostname;
        // await createDomain({ name: hostname, suspicious: decisionValue === 1 });
        await updateDomainSuspicious(hostname, decisionValue === 1);
        console.log('Domain suspicious flag updated for', hostname, '=>', decisionValue === 1);
      } catch (domainErr) {
        console.error('Failed updating domain suspicious flag', domainErr);
      }

      // Start removal animation
      setIsRemoving(true);
      
      // Wait for animation to complete, then remove from list
      setTimeout(() => {
        if (onUpdate) {
          onUpdate();
        }
      }, 300); // Animation duration
      
    } catch (error) {
      console.error('Failed to update admin decision:', error);
      setUpdating(false);
      // TODO: Show error toast
    }
  };

  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(report.url);
      // TODO: Show success toast
      console.log('URL copied to clipboard');
    } catch (err) {
      console.error('Failed to copy URL:', err);
    }
  };

  const getWebsiteFavicon = (url) => {
    try {
      const urlObj = new URL(url);
      return `https://www.google.com/s2/favicons?sz=64&domain=${urlObj.hostname}`;
    } catch (error) {
      return null;
    }
  };

  const truncateReason = (reason, maxLength = 100) => {
    if (!reason) return '';
    if (reason.length <= maxLength) return reason;
    return showFullReason ? reason : reason.substring(0, maxLength) + '...';
  };

  const getClassificationBadge = (classification) => {
    // mapping: 1 = phishing, 0 = safe
    const isPhishing = classification === 1;
    return (
      <span className={`classification-badge ${isPhishing ? 'phishing' : 'safe'}`}>
        {isPhishing ? 'Phishing' : 'Safe'}
      </span>
    );
  };

  return (
    <div className={`user-report-card ${isRemoving ? 'removing' : ''}`}>
      <div className="report-header">
        <div className="report-meta">
          <span className="report-id">Report #{report.id}</span>
          <span className="report-count">{report.reportCount} reports</span>
          <span className="report-date">{new Date(report.createdAt).toLocaleDateString('en-US')}</span>
        </div>
        <div className="url-container">
          <div className="url-with-favicon">
            <img 
              src={getWebsiteFavicon(report.url)} 
              alt="Website favicon"
              className="website-favicon"
              onError={(e) => e.target.style.display = 'none'}
            />
            <a href={report.url} target="_blank" rel="noopener noreferrer" className="url-link">
              {report.url}
            </a>
          </div>
          <button 
            className="copy-url-btn"
            onClick={handleCopyUrl}
            title="Copy URL"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" className="copy-icon">
              <path d="M16 1H4C2.9 1 2 1.9 2 3v14h2V3h12V1zm3 4H8C6.9 5 6 5.9 6 7v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
            </svg>
          </button>
        </div>
      </div>
      
      <div className="classifications">
        <div className="classification-item">
          <label>System Label</label>
          {getClassificationBadge(report.systemClassification)}
        </div>
        
        <div className="classification-item">
          <label>User Label</label>
          {getClassificationBadge(report.userClassification)}
        </div>
        
        <div className="classification-item">
          <label>Admin Decision</label>
          <div className="admin-inline-controls">
            <select 
              id={`admin-select-${report.id}`}
              value={adminDecision}
              onChange={(e) => setAdminDecision(e.target.value)}
              className="admin-dropdown-inline"
              data-value={adminDecision}
            >
              <option value="">Select</option>
              <option value="1">Phishing</option>
              <option value="0">Safe</option>
            </select>
            
            <button 
              className="confirm-btn-inline"
              onClick={handleConfirmDecision}
              disabled={!adminDecision || updating}
            >
              {updating ? '...' : '✓'}
            </button>
          </div>
        </div>
      </div>

      <div className="user-reason">
        <label>User Reason:</label>
        <p className="reason-text">
          {truncateReason(report.userReason)}
          {report.userReason && report.userReason.length > 100 && (
            <button 
              className="show-more-btn"
              onClick={() => setShowFullReason(!showFullReason)}
            >
              {showFullReason ? 'Show Less' : 'Show More'}
            </button>
          )}
        </p>
      </div>
    </div>
  );
}