import path from "path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  root: path.resolve(__dirname),
  build: {
    outDir: path.resolve(__dirname, "../src/main/resources/static"),
    emptyOutDir: false,
    rollupOptions: {
      input: {
        games: path.resolve(__dirname, "web/games.html"),
        game: path.resolve(__dirname, "web/game.html"),
        roster: path.resolve(__dirname, "roster.html")
      }
    }
  }
});
