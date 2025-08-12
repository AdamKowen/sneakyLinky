import axios from "axios";
import { TOKEN_STORAGE_KEY } from "../config/constants";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE,
  timeout: 10000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;