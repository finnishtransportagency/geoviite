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
      // the app expects /prod, /test, and /dev to be similarly proxied to the respective
      // Geoviite environments' APIs
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
