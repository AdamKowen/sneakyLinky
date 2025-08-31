import { useState } from "react";
import { useToast } from "../../../layouts/AdminLayout/ToastContext";
import { searchDomainByName } from "../../../services/domain";

export default function DomainSearchCard({ onResult }) {
  const [domainName, setDomainName] = useState("");
  const [loading, setLoading] = useState(false);
  const { error: toastError, success } = useToast();

  const handleSearch = async () => {
    if (!domainName.trim()) {
      toastError("Please enter a domain name");
      return;
    }

    setLoading(true);
    try {
      const result = await searchDomainByName(domainName.trim());
      success(`Found domain "${domainName}"`);
      if (onResult) {
        onResult({
          title: `Domain Search: ${domainName}`,
          data: Array.isArray(result) ? result : [result],
          type: 'domains'
        });
      }
    } catch (error) {
      const message = error?.response?.data?.error || error.message || "Domain not found";
      toastError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  return (
    <section className="card">
      <h2 className="card-title">Search Domain</h2>
      <p className="card-sub">GET /v1/domain/:name</p>
      <div className="inline">
        <input
          className="input"
          type="text"
          placeholder="example.com"
          value={domainName}
          onChange={(e) => setDomainName(e.target.value)}
          onKeyPress={handleKeyPress}
        />
        <button 
          className="btn" 
          onClick={handleSearch}
          disabled={loading || !domainName.trim()}
        >
          {loading ? "Searching..." : "Search"}
        </button>
      </div>
    </section>
  );
}
