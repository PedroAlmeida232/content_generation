import { logout } from "./auth.js";
import {
  redirectToLogin,
  requireAuthenticatedSession,
} from "./auth-session.js?v=20260525b";

const logoutButton = document.getElementById("dashboard-logout");
const userEmailElement = document.getElementById("dashboard-user-email");

function handleLogout() {
  logout();
  redirectToLogin("/pages/dashboard.html");
}

const session = requireAuthenticatedSession("/pages/dashboard.html");

if (session) {
  if (session.email && userEmailElement) {
    userEmailElement.textContent = session.email;
  }
  logoutButton?.addEventListener("click", handleLogout);
}
