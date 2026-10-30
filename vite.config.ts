import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Freebuff keeps HMR off; the preview serves the built bundle.
    hmr: false,
  },
  build: {
    outDir: "dist",
    chunkSizeWarningLimit: 900,
  },
});
