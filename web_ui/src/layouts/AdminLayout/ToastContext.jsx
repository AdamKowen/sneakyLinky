// src/layouts/AdminLayout/ToastContext.jsx
import { createContext, useCallback, useContext, useRef, useState } from "react";

const ToastCtx = createContext(null);

export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null);    
  const timerRef = useRef(null);
  const idRef = useRef(0);

  const show = useCallback((message, { type = "info", ttl = 2000 } = {}) => {
    
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    
    const id = ++idRef.current;
    setToast({ id, type, message, isVisible: true });

    timerRef.current = setTimeout(() => {
      setToast(prev => prev ? { ...prev, isVisible: false } : null);
      setTimeout(() => {
        setToast(null);
        timerRef.current = null;
      }, 300);
    }, ttl);
  }, []);

  const api = {
    toast,
    show,
    info:    (m, opt) => show(m, { type: "info",    ...(opt || {}) }),
    success: (m, opt) => show(m, { type: "success", ...(opt || {}) }),
    warn:    (m, opt) => show(m, { type: "warn",    ...(opt || {}) }),
    error:   (m, opt) => show(m, { type: "error",   ...(opt || {}) }),
    remove:  () => { if (timerRef.current) clearTimeout(timerRef.current); setToast(null); timerRef.current = null; },
  };

  return <ToastCtx.Provider value={api}>{children}</ToastCtx.Provider>;
}

export function useToast() {
  const ctx = useContext(ToastCtx);
  if (!ctx) throw new Error("useToast must be used within <ToastProvider>");
  return ctx;
}
