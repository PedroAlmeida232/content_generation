import { getToken, logout } from "./auth.js";

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  return normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
}

export function parseTokenClaims(token = getToken()) {
  try {
    if (!token) {
      return null;
    }

    const [, payload] = token.split(".");
    if (!payload) {
      return null;
    }

    const decoded = atob(decodeBase64Url(payload));
    return JSON.parse(decoded);
  } catch (error) {
    return null;
  }
}

export function isTokenExpired(claims) {
  if (!claims || typeof claims.exp !== "number") {
    return false;
  }

  return claims.exp * 1000 <= Date.now();
}

export function getAuthenticatedSession() {
  const token = getToken();
  if (!token) {
    return null;
  }

  const claims = parseTokenClaims(token);
  if (!claims || isTokenExpired(claims)) {
    logout();
    return null;
  }

  return {
    token,
    claims,
    email: typeof claims.sub === "string" && claims.sub.trim() ? claims.sub.trim() : null,
  };
}

export function buildLoginRedirect(nextPath = `${window.location.pathname}${window.location.search}`) {
  const next = encodeURIComponent(nextPath);
  return `/pages/login.html?next=${next}`;
}

export function redirectToLogin(nextPath) {
  logout();
  window.location.replace(buildLoginRedirect(nextPath));
}

export function redirectAuthenticatedUser(nextPath) {
  const session = getAuthenticatedSession();
  if (!session) {
    return false;
  }

  window.location.replace(nextPath);
  return true;
}

export function redirectAfterLogin(nextPath) {
  const session = getAuthenticatedSession();
  if (!session) {
    throw new Error("Nao foi possivel validar a sessao autenticada.");
  }

  window.location.replace(nextPath);
}

export function requireAuthenticatedSession(nextPath = `${window.location.pathname}${window.location.search}`) {
  const session = getAuthenticatedSession();
  if (!session) {
    redirectToLogin(nextPath);
    return null;
  }

  return session;
}
