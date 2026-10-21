import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxy /api y /health al backend Spring Boot en dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      "/health": { target: "http://localhost:8080", changeOrigin: true },
    },
  },
});
