// src/layouts/AdminLayout/ToastArea.jsx
import { useToast } from "./ToastContext";
import "./toast.css"

export default function ToastArea() {
  const { toast, remove } = useToast();
  return (
    <div className="toast-area" aria-live="polite">
      {toast && (
        <div
          key={toast.id}
          className={`toast toast-${toast.type} ${!toast.isVisible ? 'toast-fade-out' : ''}`}
          role="status"
          onClick={remove}   
        >
          {toast.message}
        </div>
      )}
    </div>
  );
}
