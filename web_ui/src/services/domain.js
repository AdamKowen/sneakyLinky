import api from "./apiClient";

export async function getDomainsLimit(params) {
  let n, sortBy, dir;

  if (typeof params === 'number' || typeof params === 'string') {
    n = parseInt(params, 10);
  } else if (params && typeof params === 'object') {
    n = parseInt(params.limit ?? params.n ?? params.count, 10);
    sortBy = params.sortBy ?? params.orderBy ?? 'createdAt';           
    dir = String(params.dir ?? params.order ?? 'ASC').toUpperCase();    
  } else {
    throw new Error('Invalid params to getDomainsLimit');
  }

  if (!Number.isFinite(n) || n <= 0) throw new Error('Invalid limit');

  const qs = new URLSearchParams();
  if (sortBy) qs.set('sortBy', sortBy);
  if (dir) qs.set('dir', dir);

  const url = `/domain/limit/${n}${qs.toString() ? `?${qs.toString()}` : ''}`;


  const { data } = await api.get(url);
  return data;
}

export async function searchDomainByName(domainName) {
  const { data } = await api.get(`/domain/${encodeURIComponent(domainName)}`);
  return data;
}

export async function deleteDomain(domainName) {
  const { data } = await api.delete(`/domain/${encodeURIComponent(domainName)}`);
  return data;
}

export async function updateDomainSuspicious(domainName, suspicious) {
  const { data } = await api.patch(`/domain/${encodeURIComponent(domainName)}`, { suspicious });
  return data;
}

// NEW: Create a domain
export async function createDomain({ name, suspicious }) {
  const { data } = await api.post(`/domain`, { name, suspicious });
  return data;
}

// NEW: Get domain statistics
export async function getDomainStats() {
  const { data } = await api.get(`/domain/stats`);
  return data;
}
