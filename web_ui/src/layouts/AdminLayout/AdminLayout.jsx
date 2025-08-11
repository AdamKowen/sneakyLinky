import { Outlet } from "react-router-dom";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";
import ToastArea from "./ToastArea";
import { ToastProvider } from "./ToastContext";
import "./adminLayout.css";

export default function AdminLayout() {
  return (
    <ToastProvider>
      <div className="admin-shell">
        <Sidebar />
        <div className="main">
          <Topbar />
          <ToastArea />
          <main className="content">
            <Outlet />
          </main>
        </div>
      </div>
    </ToastProvider>
  );
}
