import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { captureInstallPrompt, registerServiceWorker, registerWebProtocol, watchLaunchFiles } from "./lib/pwa";
import "./index.css";

// Installed-app behaviour: offline shell, web+oru links, file hand-offs and the
// browser's install prompt. All of it no-ops where the browser has no support.
registerServiceWorker();
registerWebProtocol();
watchLaunchFiles();
captureInstallPrompt();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
