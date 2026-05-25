import * as storage from "./storage.js";

const AUTH_API_BASE = "/api/auth";
const AUTH_REQUEST_TIMEOUT_MS = 15000;

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

async function performAuthRequest(path, payload) {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), AUTH_REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(`${AUTH_API_BASE}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    return await parseAuthResponse(response);
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new AuthError("A requisicao demorou mais do que o esperado. Tente novamente.");
    }

    if (error instanceof AuthError) {
      throw error;
    }

    throw new AuthError("Nao foi possivel conectar ao servico de autenticacao.");
  } finally {
    window.clearTimeout(timeoutId);
  }
}

/**
 * @param {{ email: string, password: string }} credentials
 * @returns {Promise<{ userId: string, email: string, expiresIn: number, tokenType: string }>}
 */
export async function login({ email, password }) {
  const data = await performAuthRequest("/auth/login", { email, password });
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
  return performAuthRequest("/auth/register", { email, password, name });
}

export function logout() {
  storage.clearToken();
}

export function getToken() {
  return storage.getToken();
}
