import { useState } from "react";
import { useToast } from "../../../layouts/AdminLayout/ToastContext";
import { createDomain } from "../../../services/domain";

// Optional: very light client check; server still validates
function isLikelyDomain(s) {
  return /\./.test(s) && s.length >= 3 && s.length <= 253;
}

export default function DomainCreateCard({ onResult, onMutate }) {
  const [name, setName] = useState("");
  const [suspicious, setSuspicious] = useState(false);
  const [busy, setBusy] = useState(false);
  const { success, error } = useToast();

  async function handleCreate() {
    const trimmed = name.trim().toLowerCase();
    if (!trimmed) {
      error("Please enter a domain name");
      return;
    }

    setBusy(true);
    try {
      const created = await createDomain({ name: trimmed, suspicious: suspicious ? 1 : 0 });
      success(`Domain "${created?.name || trimmed}" created`);
      onMutate?.(); // Notify parent to refresh stats
      setName("");
      setSuspicious(false);
      if (onResult) {
        onResult({
          title: `Created: ${created?.name || trimmed}`,
          data: [created || { name: trimmed, suspicious: suspicious ? 1 : 0, access_count: 0 }],
          type: 'domains'
        });
      }
    } catch (e) {
      const msg = e?.response?.data?.error || e.message || "Failed to create domain";
      error(msg);
    } finally {
      setBusy(false);
    }
  }

  function onKeyDown(e) {
    if (e.key === 'Enter') handleCreate();
  }

  return (
    <section className="card">
      <h2 className="card-title">Add Domain</h2>
      <p className="card-sub">POST /v1/domain</p>
      <div className="inline" style={{ alignItems: 'center' }}>
        <input
          className="input"
          type="text"
          placeholder="example.com"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <input
            type="checkbox"
            checked={suspicious}
            onChange={(e) => setSuspicious(e.target.checked)}
          />
          Suspicious
        </label>
        <button className="btn" onClick={handleCreate} disabled={busy || name.trim() === ""}>
          {busy ? 'Creating...' : 'Create'}
        </button>
      </div>
    </section>
  );
}
