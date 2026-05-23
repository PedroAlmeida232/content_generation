import { getApiKey, getToken } from "./storage.js";

const AUTH_BASE = "/api/auth";
const AI_BASE = "/api/ai";

export class ApiError extends Error {
  constructor(message, { status = 0, body = null } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

async function parseApiResponse(response) {
  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const body = isJson ? await response.json().catch(() => null) : null;

  if (!response.ok) {
    const message = body?.message || `Request failed with status ${response.status}`;
    throw new ApiError(message, { status: response.status, body });
  }

  return body;
}

function buildHeaders(options) {
  const { auth = true, openaiKey = false, headers = {}, body } = options;
  const finalHeaders = new Headers({
    Accept: "application/json",
    ...headers,
  });

  if (body !== undefined && body !== null && !finalHeaders.has("Content-Type")) {
    if (typeof body === "string") {
      finalHeaders.set("Content-Type", "application/json");
    }
  }

  if (auth) {
    const token = getToken();
    if (!token) {
      throw new ApiError("Not authenticated", { status: 401 });
    }
    finalHeaders.set("Authorization", `Bearer ${token}`);
  }

  if (openaiKey) {
    const key = getApiKey();
    if (!key) {
      throw new ApiError("OpenAI API key not configured", { status: 400 });
    }
    finalHeaders.set("X-OpenAI-Key", key);
  }

  return finalHeaders;
}

/**
 * @param {string} baseUrl
 * @param {string} path
 * @param {RequestInit & { auth?: boolean, openaiKey?: boolean }} options
 */
export async function request(baseUrl, path, options = {}) {
  const { auth = true, openaiKey = false, headers, body, ...init } = options;
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const finalHeaders = buildHeaders({ auth, openaiKey, headers, body });

  const response = await fetch(`${baseUrl}${normalizedPath}`, {
    ...init,
    headers: finalHeaders,
    body,
  });

  return parseApiResponse(response);
}

function createClient(baseUrl, defaultAuth) {
  return {
    get(path, options = {}) {
      return request(baseUrl, path, { ...options, method: "GET", auth: defaultAuth });
    },
    post(path, body, options = {}) {
      return request(baseUrl, path, {
        ...options,
        method: "POST",
        auth: defaultAuth,
        body: JSON.stringify(body),
      });
    },
    put(path, body, options = {}) {
      return request(baseUrl, path, {
        ...options,
        method: "PUT",
        auth: defaultAuth,
        body: JSON.stringify(body),
      });
    },
    delete(path, options = {}) {
      return request(baseUrl, path, { ...options, method: "DELETE", auth: defaultAuth });
    },
  };
}

export const authApi = createClient(AUTH_BASE, true);

export const aiApi = {
  ...createClient(AI_BASE, true),
  post(path, body, options = {}) {
    return request(AI_BASE, path, {
      ...options,
      method: "POST",
      auth: true,
      openaiKey: options.openaiKey === true,
      body: JSON.stringify(body),
    });
  },
};

export const publicAiApi = createClient(AI_BASE, false);

export function getCurrentUser() {
  return authApi.get("/users/me");
}

export function getAiProfile() {
  return aiApi.get("/me");
}
