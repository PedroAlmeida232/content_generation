/** Browser-only localStorage helpers for session data. */

export const STORAGE_KEYS = {
  JWT: "cg.jwt",
  OPENAI_API_KEY: "cg.openai_api_key",
};

function getStorage() {
  if (typeof localStorage === "undefined") {
    throw new Error("localStorage is not available in this environment");
  }
  return localStorage;
}

function requireNonEmpty(value, label) {
  const normalized = typeof value === "string" ? value.trim() : "";
  if (!normalized) {
    throw new Error(`${label} must be a non-empty string`);
  }
  return normalized;
}

export function saveToken(token) {
  getStorage().setItem(STORAGE_KEYS.JWT, requireNonEmpty(token, "token"));
}

export function getToken() {
  const value = getStorage().getItem(STORAGE_KEYS.JWT);
  return value?.trim() || null;
}

export function clearToken() {
  getStorage().removeItem(STORAGE_KEYS.JWT);
}

export function saveApiKey(apiKey) {
  getStorage().setItem(
    STORAGE_KEYS.OPENAI_API_KEY,
    requireNonEmpty(apiKey, "OpenAI API key"),
  );
}

export function getApiKey() {
  const value = getStorage().getItem(STORAGE_KEYS.OPENAI_API_KEY);
  return value?.trim() || null;
}

export function clearApiKey() {
  getStorage().removeItem(STORAGE_KEYS.OPENAI_API_KEY);
}

export function clearSession() {
  clearToken();
  clearApiKey();
}

export function hasToken() {
  return getToken() !== null;
}
