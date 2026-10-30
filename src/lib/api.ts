import type { AuthResponse } from "./types";

const rawBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "/api/v1";

export const API_BASE = rawBase.replace(/\/+$/, "");
export const API_ORIGIN = /^https?:\/\//.test(API_BASE) ? new URL(API_BASE).origin : "";

const TOKEN_KEY = "oru.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export interface ApiErrorDetails {
  field?: string;
  message: string;
}

export class ApiError extends Error {
  code: string;
  status: number;
  details: ApiErrorDetails[];

  constructor(status: number, code: string, message: string, details: ApiErrorDetails[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

/** Media paths come back relative to the API host; resolve them for the page origin. */
export function mediaUrl(path: string | null | undefined): string | undefined {
  if (!path) return undefined;
  if (/^https?:\/\//.test(path)) return path;
  if (!API_ORIGIN) return path;
  if (path.startsWith(API_ORIGIN)) return path;
  return `${API_ORIGIN}${path}`;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  token?: string | null;
  signal?: AbortSignal;
}

async function toError(response: Response): Promise<ApiError> {
  let code = "request_failed";
  let message = "Something went wrong. Try again.";
  let details: ApiErrorDetails[] = [];
  try {
    const payload = await response.json();
    const error = payload?.error;
    if (error?.code) code = error.code;
    if (error?.message) message = error.message;
    if (Array.isArray(error?.details)) details = error.details;
  } catch {
    // Non-JSON body (a proxy error, most likely). Keep the generic message.
  }
  return new ApiError(response.status, code, message, details);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, token = getToken(), signal } = options;
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const response = await fetch(`${API_BASE}${path}`, { method, headers, body: payload, signal });
  if (!response.ok) throw await toError(response);
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Multipart upload with real progress events, because uploads here are real files. */
export function upload<T>(
  path: string,
  form: FormData,
  onProgress?: (fraction: number) => void,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE}${path}`);
    const token = getToken();
    if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) onProgress(event.loaded / event.total);
    };
    xhr.onerror = () => reject(new ApiError(0, "network_error", "The connection dropped. Check it and try again."));
    xhr.onload = () => {
      let parsed: unknown = null;
      try {
        parsed = JSON.parse(xhr.responseText || "null");
      } catch {
        parsed = null;
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(parsed as T);
        return;
      }
      const error = (parsed as { error?: { code?: string; message?: string } } | null)?.error;
      reject(
        new ApiError(
          xhr.status,
          error?.code ?? "upload_failed",
          error?.message ?? "The upload did not go through. Try it once more.",
        ),
      );
    };
    xhr.send(form);
  });
}

export const auth = {
  register: (input: { email: string; handle: string; display_name: string; password: string }) =>
    request<AuthResponse>("/auth/register", { method: "POST", body: input, token: null }),
  login: (input: { email: string; password: string }) =>
    request<AuthResponse>("/auth/login", { method: "POST", body: input, token: null }),
  me: () => request<{ user: AuthResponse["user"] }>("/auth/me"),
};
