import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const API_PATHS = ["/game", "/search", "/recommendation", "/favorite", "/register", "/login", "/logout"];

export default defineConfig({
  plugins: [react()],
  build: {
    // Spring Boot serves the built app from here. Assets go under /static/**, which the
    // security config already lets through without a login.
    outDir: "../src/main/resources/public",
    assetsDir: "static",
    emptyOutDir: true,
  },
  server: {
    // `npm run dev` talks to the backend running on :8080
    proxy: Object.fromEntries(API_PATHS.map((path) => [path, "http://localhost:8080"])),
  },
});
