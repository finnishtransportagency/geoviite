import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Convenience proxy: if the Geoviite backend does not allow CORS from the dev
    // server origin, set the API URL prefix in the app to just "/geoviite".
    proxy: {
      "/local": {
        target: "http://localhost:8080/",
        rewrite: (path) => path.replace(/^\/local/, "/geoviite"),
        changeOrigin: true,
      },
      "/test": {
        target: "<test-geoviite-swagger url>",
        rewrite: (path) => path.replace(/^\/local/, "/geoviite"),
        changeOrigin: true,
      }
      // set up "/dev", "/prod" same as test
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
