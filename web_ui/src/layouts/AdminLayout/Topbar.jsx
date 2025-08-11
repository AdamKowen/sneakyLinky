import { useAuth } from "../../hooks/useAuth";
import { FiLogOut } from "react-icons/fi"; // Feather icons
import { useNavigate } from "react-router-dom";


export default function Topbar() {
  const { logout } = useAuth();
  const navigate = useNavigate();
 

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="topbar">
      <h1 className="topbar-title">SneakyLinky</h1>
      <div className="topbar-actions">
        <button
          className="btn logout-btn"
          onClick={handleLogout}
          title="Log out"
        >
          <FiLogOut size={18} style={{ gap: "6px", alignItems: "center" }} /> Logout
        </button>
        <img
          className="avatar"
          src="/logo.png"
          alt="App logo"
          width={32}
          height={32}
        />
      </div>
    </header>
  );
}
