import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";

/**
 * Cliente HTTP. Inyecta el access token y, ante un 401, intenta refrescar
 * con el refresh token una sola vez antes de desistir.
 */
const api = axios.create({ baseURL: "/api/v1" });

const TOKEN_KEY = "ft_access_token";
const REFRESH_KEY = "ft_refresh_token";

export function setTokens(access: string, refresh: string) {
  localStorage.setItem(TOKEN_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) {
    throw new Error("No refresh token");
  }
  const resp = await axios.post("/api/v1/auth/refresh", { refreshToken });
  setTokens(resp.data.accessToken, resp.data.refreshToken);
  return resp.data.accessToken;
}

api.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean };
    const isAuthEndpoint = original?.url?.includes("/auth/");

    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint) {
      original._retried = true;
      try {
        refreshing = refreshing ?? refreshAccessToken();
        const newToken = await refreshing;
        refreshing = null;
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      } catch {
        refreshing = null;
        clearTokens();
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  },
);

/** Extrae un mensaje de error legible en español de una respuesta de la API. */
export function errorMessage(e: unknown, fallback = "Ocurrió un error inesperado"): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { error?: string } | undefined;
    if (data?.error) {
      return data.error;
    }
    if (e.response?.status === 403) {
      return "No tiene permisos para realizar esta acción";
    }
    if (e.code === "ERR_NETWORK") {
      return "No se pudo conectar con el servidor";
    }
  }
  return fallback;
}

export default api;
