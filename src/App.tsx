import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { Landing } from "./pages/Landing";
import { AuthPage } from "./pages/Auth";
import { About } from "./pages/About";

/**
 * The site is a download page now; the app is the product.
 *
 * `/auth` stays because Google sign-in has to happen in a browser: the app
 * opens it, it signs the account in and hands the app a one-time code, and the
 * app carries on from there.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/about" element={<About />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
