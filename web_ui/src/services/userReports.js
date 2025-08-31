import api from "./apiClient";

export async function getUserReports(params) {
  const { data } = await api.get(`/userReports/top/${params}`);
  return data;
}

/**
 * Update admin decision for a specific report
 * @param {string} uuid - The report UUID
 * @param {number} adminDecision - Admin decision (0 = safe, 1 = phishing)
 * @returns {Promise} API response with updated report
 */
export async function updateAdminDecision(uuid, adminDecision) {
  try {
    const { data } = await api.put(`/userReports/${uuid}/adminDecision`, {
      adminDecision: parseInt(adminDecision)
    });
    return data;
  } catch (error) {
    console.error(`Error updating admin decision for report ${uuid}:`, error);
    throw error;
  }
}

/**
 * Get user reports statistics
 * @returns {Promise} API response with statistics data { total: number, unclassified: number, classified: number }
 */
export async function getUserReportsStats() {
  try {
    const { data } = await api.get('/userReports/stats');
    return data;
  } catch (error) {
    console.error('Error fetching user reports stats:', error);
    throw error;
  }
}




