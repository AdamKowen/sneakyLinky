// src/services/auth.js
import api from "./apiClient";

export async function login({ email, password }) {
  const { data } = await api.post("/auth/login", { email, password });
  // מצופה לקבל data.token מהשרת
  return data; 
}
