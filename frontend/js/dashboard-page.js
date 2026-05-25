import { getToken, logout } from "./auth.js";

const logoutButton = document.getElementById("dashboard-logout");
const userEmailElement = document.getElementById("dashboard-user-email");

function buildLoginRedirect() {
  const next = encodeURIComponent("/pages/dashboard.html");
  return `/pages/login.html?next=${next}`;
}

function readStoredEmail() {
  try {
    const token = getToken();
    if (!token) {
      return null;
    }

    const [, payload] = token.split(".");
    if (!payload) {
      return null;
    }

    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const decoded = atob(padded);
    const claims = JSON.parse(decoded);
    return typeof claims.sub === "string" && claims.sub.trim() ? claims.sub.trim() : null;
  } catch (error) {
    return null;
  }
}

function redirectToLogin() {
  window.location.replace(buildLoginRedirect());
}

function handleLogout() {
  logout();
  redirectToLogin();
}

const token = getToken();

if (!token) {
  redirectToLogin();
} else {
  const email = readStoredEmail();
  if (email && userEmailElement) {
    userEmailElement.textContent = email;
  }
  logoutButton?.addEventListener("click", handleLogout);
}
