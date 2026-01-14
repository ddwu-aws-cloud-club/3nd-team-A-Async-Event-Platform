import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/admin": {
        target: "http://localhost:8082",
        changeOrigin: true,
      },
      "/api": {
        target: "http://alb-async-ingest-1521062058.ap-northeast-2.elb.amazonaws.com/",
        changeOrigin: true,
      },
    },
  },
});
