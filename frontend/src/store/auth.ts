import { create } from "zustand";
import { persist } from "zustand/middleware";
import { clearTokens, setTokens } from "../api/client";
import { authApi } from "../api/endpoints";

interface AuthState {
  userId: string | null;
  username: string | null;
  fullName: string | null;
  role: string | null;
  permissions: string[];
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasPermission: (perm: string) => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      userId: null,
      username: null,
      fullName: null,
      role: null,
      permissions: [],
      isAuthenticated: false,

      login: async (username, password) => {
        const res = await authApi.login(username, password);
        setTokens(res.accessToken, res.refreshToken);
        set({
          userId: res.userId,
          username: res.username,
          fullName: res.fullName,
          role: res.role,
          permissions: res.permissions,
          isAuthenticated: true,
        });
      },

      logout: () => {
        clearTokens();
        set({
          userId: null,
          username: null,
          fullName: null,
          role: null,
          permissions: [],
          isAuthenticated: false,
        });
      },

      hasPermission: (perm) => get().permissions.includes(perm),
    }),
    {
      name: "ft_auth",
      // El token vive en localStorage (client.ts); aquí solo el perfil/permisos.
      partialize: (s) => ({
        userId: s.userId,
        username: s.username,
        fullName: s.fullName,
        role: s.role,
        permissions: s.permissions,
        isAuthenticated: s.isAuthenticated,
      }),
    },
  ),
);
