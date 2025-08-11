export default function DomainRow({ domain, onDelete, onToggleSuspicious }) {
  return (
    <tr>
      <td className="domain-name">{domain.name}</td>
      <td className={`suspicious ${domain.suspicious ? 'suspicious-yes' : 'suspicious-no'}`}>
        {domain.suspicious ? ' Yes⚠️' : ' No✅'}
      </td>
      <td className="access-count">{domain.access_count || 0}</td>
      <td className="timestamp">{new Date(domain.createdAt).toLocaleString()}</td>
      <td className="timestamp">{new Date(domain.updatedAt).toLocaleString()}</td>
      <td className="actions">
        <div className="action-buttons">
          <button
            className={`action-btn toggle-btn ${domain.suspicious ? 'btn-safe' : 'btn-suspicious'}`}
            onClick={() => onToggleSuspicious(domain.name, domain.suspicious)}
            title={domain.suspicious ? 'Mark as safe' : 'Mark as suspicious'}
          >
            {domain.suspicious ? '✅' : '⚠️'}
          </button>
          <button
            className="action-btn delete-btn"
            onClick={() => onDelete(domain.name)}
            title="Delete domain"
          >
            🗑️
          </button>
        </div>
      </td>
    </tr>
  );
}
