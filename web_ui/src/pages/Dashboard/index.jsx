import { useState } from "react";
import "./dashboard.css";
import DashboardGrid from "./components/DashboardGrid";
import DomainSearchCard from "./components/DomainByNameCard";
import DomainLimitCard from "./components/DomainLimitCard";
import DomainCreateCard from "./components/DomainCreateCard";
import DomainStatsCard from "./components/DomainStatsCard";
import ResultsCard from "./components/ResultsCard";
import { getDomainsLimit } from "../../services/domain";


export default function DashboardPage() {
  const [statsRefreshKey, setStatsRefreshKey] = useState(0);
  const bumpStats = () => setStatsRefreshKey(k => k + 1);

  const [result, setResult] = useState(null);
  const [lastQuery, setLastQuery] = useState(null);

  const handleResult = (newResult) => {
    setResult(newResult);
    if (newResult.type === 'domains' && newResult.title.includes('Limit:')) {
      const limitMatch = newResult.title.match(/Limit: (\d+)/);
      if (limitMatch) {
        setLastQuery({
          type: 'limit',
          value: parseInt(limitMatch[1])
        });
      }
    } else if (newResult.type === 'domains' && newResult.title.includes('Search:')) {
      const searchMatch = newResult.title.match(/Search: (.+)/);
      if (searchMatch) {
        setLastQuery({
          type: 'search',
          value: searchMatch[1]
        });
      }
    }
  };

  const handleRefresh = async (updatedResult = null) => {
    if (updatedResult) {
      setResult(updatedResult);
      return;
    }
    if (!lastQuery) return;
    try {
      let data;
      let title;
      if (lastQuery.type === 'limit') {
        data = await getDomainsLimit(lastQuery.value);
        title = `Domains (Limit: ${lastQuery.value})`;
      } else if (lastQuery.type === 'first') {
        return;
      }
      if (data) {
        setResult({
          title,
          data,
          type: 'domains'
        });
      }
    } catch (error) {
      console.error('Failed to refresh data:', error);
    }
  };

  return (
    <DashboardGrid>
      <DomainStatsCard refreshKey={statsRefreshKey} />
      <DomainSearchCard onResult={handleResult} />
      <DomainLimitCard onResult={handleResult} />
      <DomainCreateCard onResult={handleResult} onMutate={bumpStats} />
      <ResultsCard result={result} onRefresh={handleRefresh} onMutate={bumpStats} />
    </DashboardGrid>
  );
}
