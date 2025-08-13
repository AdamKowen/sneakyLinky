import { useState, useEffect } from 'react';
import "./userreports.css";
import UserReportsGrid from "./components/UserReportsGrid";
import UserReportCard from "./components/UserReportCard";
import UserReportStatsCard from "./components/UserReportStatsCard";
import { getUserReports } from "../../services/userReports";

export default function UserReports() {
    const [reports, setReports] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [statsRefreshKey, setStatsRefreshKey] = useState(0);

    useEffect(() => {
        const fetchReports = async () => {
            try {
                setLoading(true);
                const data = await getUserReports(10);
                setReports(data);
            } catch (err) {
                console.error('Error fetching reports:', err);
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };

        fetchReports();
    }, []);

    const handleReportUpdate = (reportId) => {
        // Remove the specific report from the list with smooth animation
        setReports(prevReports => prevReports.filter(report => report.id !== reportId));
        setStatsRefreshKey(k => k + 1); // trigger stats refresh
    };

    if (loading) {
        return (
            <UserReportsGrid>
                <div className="loading-state">Loading reports...</div>
            </UserReportsGrid>
        );
    }

    if (error && reports.length === 0) {
        return (
            <UserReportsGrid>
                <div className="error-state">
                    <p>Error loading reports: {error}</p>
                    <button onClick={() => window.location.reload()}>Retry</button>
                </div>
            </UserReportsGrid>
        );
    }

    return (
        <UserReportsGrid>
            <h1>Users Reports</h1>
            <UserReportStatsCard refreshKey={statsRefreshKey} />
            {reports.map(report => (
                <UserReportCard 
                    key={report.id} 
                    report={report} 
                    onUpdate={() => handleReportUpdate(report.id)}
                />
            ))}
        </UserReportsGrid>
    );
}
