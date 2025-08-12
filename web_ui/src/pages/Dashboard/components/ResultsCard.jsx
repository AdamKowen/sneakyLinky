import "./results.css";
import { useToast } from "../../../layouts/AdminLayout/ToastContext";
import { deleteDomain, updateDomainSuspicious } from "../../../services/domain";
import DomainRow from "./DomainRow";

export default function ResultsCard({ result, onRefresh, onMutate }) {
  const { success, error } = useToast();

  const handleDelete = async (domainName) => {
    if (!confirm(`Are you sure you want to delete "${domainName}"?`)) return;

    try {
      await deleteDomain(domainName);
      success(`Domain "${domainName}" deleted successfully`);
      onMutate?.(); // Notify parent to refresh stats

      if (result?.data) {
        const updatedData = result.data.filter(d => d.name !== domainName);
        onRefresh?.({ ...result, data: updatedData });
      }
    } catch (err) {
      error(err?.response?.data?.error || err.message || "Failed to delete domain");
    }
  };

  const handleToggleSuspicious = async (domainName, currentSuspicious) => {
    const newSuspicious = currentSuspicious ? 0 : 1;
    try {
      await updateDomainSuspicious(domainName, newSuspicious);
      success(`Domain "${domainName}" ${newSuspicious ? "marked as suspicious" : "marked as safe"}`);
      onMutate?.(); // Notify parent to refresh stats

      if (result?.data) {
        const updatedData = result.data.map(d =>
          d.name === domainName ? { ...d, suspicious: newSuspicious } : d
        );
        onRefresh?.({ ...result, data: updatedData });
      }
    } catch (err) {
      error(err?.response?.data?.error || err.message || "Failed to update suspicious status");
    }
  };

  if (!result) {
    return (
      <section className="card card-span">
        <h2 className="card-title">Results</h2>
        <p className="card-sub">No data to display</p>
      </section>
    );
  }
  //
  const recordCount = Array.isArray(result.data) ? result.data.length : 0;

  return (
    <section className="card card-span">
      <h2 className="card-title">
        {result.title} 
        <span className="record-count">({recordCount} record{recordCount !== 1 ? 's' : ''})</span>
      </h2>
      <div className="card-content">
        {(!Array.isArray(result.data) || result.data.length === 0) ? (
          <p>No results found</p>
        ) : (
          <div className="table-container">
            <table className="results-table">
              <thead>
                <tr>
                  <th>Domain Name</th>
                  <th>Suspicious</th>
                  <th>Access Count</th>
                  <th>Created At</th>
                  <th>Updated At</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.data.map((domain, idx) => (
                  <DomainRow
                    key={domain.name || idx}
                    domain={domain}
                    onDelete={handleDelete}
                    onToggleSuspicious={handleToggleSuspicious}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
