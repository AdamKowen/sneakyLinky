import { useState } from "react";
import { useToast } from "../../../layouts/AdminLayout/ToastContext";
import { getDomainsLimit } from "../../../services/domain";

export default function DomainLimitCard({ onResult }) {
  const [n, setN] = useState("");
  const [loading, setLoading] = useState(false);
  const [orderBy, setOrderBy] = useState("createdAt"); // עמודה למיון - ברירת מחדל
  const [order, setOrder] = useState("asc"); // כיוון המיון
  const { error: toastError, success } = useToast();

  const onChange = (e) => {
    const v = e.target.value;
    if (/^\d*$/.test(v)) {
      setN(v);
    } else {
      toastError("please enter a valid number!"); // show error toast
    }
  };

  const handleFetch = async () => {
    if (!n || parseInt(n) <= 0) {
      toastError("Please enter a valid number greater than 0");
      return;
    }

    setLoading(true);
    try {
      // הוספת פרמטרים נוספים לקריאה
      const params = {
        limit: parseInt(n),
        orderBy: orderBy,
        order: order
      };
      
      const result = await getDomainsLimit(params);
      success(`Fetched ${Array.isArray(result) ? result.length : 1} domains`);
      if (onResult) {
        onResult({
          title: `Domains (Limit: ${n}, Order: ${orderBy} ${order})`,
          data: result,
          type: 'domains'
        });
      }
    } catch (error) {
      const message = error?.response?.data?.error || error.message || "Failed to fetch domains";
      toastError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="card">
      <h2 className="card-title">Limit with Filters</h2>
      <p className="card-sub">GET /v1/domain/limit/:n with order options</p>
      
      {/* מספר התוצאות */}
      <div className="inline">
        <label>Limit:</label>
        <input
          className="input"
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          placeholder="Enter number"
          value={n}
          onChange={onChange}
        />
        <button 
          className="btn" 
          onClick={handleFetch}
          disabled={loading || !n}
        >
          {loading ? "Fetching..." : "Fetch Filtered"}
        </button>
      </div>

      {/* אפשרויות מיון - אחת לצד השנייה */}
      <div className="inline">
        <label>Order by:</label>
        <select 
          className="input" 
          value={orderBy} 
          onChange={(e) => setOrderBy(e.target.value)}
        >
          <option value="name">Name (Domain)</option>
          <option value="access_count">Access Count</option>
          <option value="createdAt">Created Date</option>
          <option value="updatedAt">Updated Date</option>
        </select>
        
        <select 
          className="input" 
          value={order} 
          onChange={(e) => setOrder(e.target.value)}
        >
          <option value="asc">Ascending (↑)</option>
          <option value="desc">Descending (↓)</option>
        </select>
      </div>
    </section>
  );
}
