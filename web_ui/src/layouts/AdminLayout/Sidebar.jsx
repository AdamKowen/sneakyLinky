import { NavLink } from "react-router-dom";
import { useUserInfo } from "../../hooks/useUserInfo";

export default function Sidebar() {
  const { email } = useUserInfo();
  return (
    <aside className="sidebar">
      <div className="brand">
        SneakyLinky<br />{email || "Admin"}
      </div>

      <nav className="nav">
        <NavLink to="/dashboard" className="nav-item">Domains</NavLink>
        <NavLink to="/UserReports" className="nav-item">User Reports</NavLink>
        {/* הוסף קישורים בהמשך */}
      </nav>

      <div className="sidebar-footer">▮▮▮</div>
    </aside>
  );
}
