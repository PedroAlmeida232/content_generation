import * as storage from "./storage.js";

const AUTH_API_BASE = "/api/auth";

export class AuthError extends Error {
  constructor(message, { status = 0, fields = {} } = {}) {
    super(message);
    this.name = "AuthError";
    this.status = status;
    this.fields = fields;
  }
}

async function parseAuthResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const body = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    const message = body?.message || `Request failed with status ${response.status}`;
    throw new AuthError(message, {
      status: response.status,
      fields: body?.errors || {},
    });
  }

  return body;
}

/**
 * @param {{ email: string, password: string }} credentials
 * @returns {Promise<{ userId: string, email: string, expiresIn: number, tokenType: string }>}
 */
export async function login({ email, password }) {
  const response = await fetch(`${AUTH_API_BASE}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ email, password }),
  });

  const data = await parseAuthResponse(response);
  storage.saveToken(data.token);

  return {
    userId: data.userId,
    email: data.email,
    expiresIn: data.expiresIn,
    tokenType: data.tokenType,
  };
}

/**
 * @param {{ email: string, password: string, name: string }} payload
 * @returns {Promise<{ id: string, email: string, name: string }>}
 */
export async function register({ email, password, name }) {
  const response = await fetch(`${AUTH_API_BASE}/auth/register`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ email, password, name }),
  });

  return parseAuthResponse(response);
}

export function logout() {
  storage.clearToken();
}

export function getToken() {
  return storage.getToken();
}
