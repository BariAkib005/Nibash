import type {
  ApiBuilding,
  ApiResident,
  ApiStaff,
  ApiUnit,
  ApiUser,
  AuthResponse,
  DirectoryResponse,
  Page,
  SessionResponse,
  SignupPayload,
} from '../types'

const TOKEN_KEY = 'nibash.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * The backend reports business failures as `{"detail": "..."}` and validation failures as a
 * field map `{"email": ["..."]}` (spec §11). Both are preserved so forms can show inline errors.
 */
export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string[]>

  constructor(status: number, message: string, fieldErrors: Record<string, string[]> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors[field]?.[0]
  }
}

type RequestOptions = {
  method?: string
  body?: unknown
  auth?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options

  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = getToken()
  if (auth && token) headers['Authorization'] = `Token ${token}`

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  // 401 means the token is gone or invalid — drop it so the app falls back to login.
  if (response.status === 401) {
    clearToken()
  }

  const text = await response.text()
  const payload = text ? safeParse(text) : null

  if (!response.ok) {
    throw toApiError(response.status, payload)
  }

  return payload as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return { detail: text }
  }
}

function toApiError(status: number, payload: unknown): ApiError {
  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>

    if (typeof record.detail === 'string') return new ApiError(status, record.detail)
    if (typeof record.error === 'string') return new ApiError(status, record.error)

    const fieldErrors: Record<string, string[]> = {}
    for (const [key, value] of Object.entries(record)) {
      if (Array.isArray(value)) fieldErrors[key] = value.map(String)
      else if (typeof value === 'string') fieldErrors[key] = [value]
    }
    const first = Object.values(fieldErrors)[0]?.[0]
    return new ApiError(status, first ?? 'Something went wrong.', fieldErrors)
  }
  return new ApiError(status, 'Something went wrong.')
}

/** Builds a query string, dropping empty values so we never send `?status=`. */
function qs(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const out = search.toString()
  return out ? `?${out}` : ''
}

export const api = {
  // ---------------------------------------------------------------- auth
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login/', { method: 'POST', body: { email, password }, auth: false }),

  signup: (payload: SignupPayload) =>
    request<AuthResponse>('/api/auth/signup/', { method: 'POST', body: payload, auth: false }),

  logout: () => request<{ detail: string }>('/api/auth/logout/', { method: 'POST' }),

  me: () => request<SessionResponse>('/api/auth/me/'),

  // ---------------------------------------------------------------- registry
  buildings: (page = 1) => request<Page<ApiBuilding>>(`/api/buildings/${qs({ page })}`),

  units: (params: { page?: number; building_id?: number; status?: string } = {}) =>
    request<Page<ApiUnit>>(`/api/units/${qs(params)}`),

  createUnit: (body: Record<string, unknown>) =>
    request<ApiUnit>('/api/units/', { method: 'POST', body }),

  updateUnit: (id: number, body: Record<string, unknown>) =>
    request<ApiUnit>(`/api/units/${id}/`, { method: 'PATCH', body }),

  deleteUnit: (id: number) => request<void>(`/api/units/${id}/`, { method: 'DELETE' }),

  residents: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiResident>>(`/api/residents/${qs(params)}`),

  updateResident: (id: number, body: Record<string, unknown>) =>
    request<ApiResident>(`/api/residents/${id}/`, { method: 'PATCH', body }),

  staff: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiStaff>>(`/api/staff/${qs(params)}`),

  users: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiUser>>(`/api/users/${qs(params)}`),

  directory: (params: { search?: string; building_id?: number } = {}) =>
    request<DirectoryResponse>(`/api/directory/${qs(params)}`),

  // ---------------------------------------------------------------- settings
  settings: (buildingId?: number) =>
    request<{ user: ApiUser; building: ApiBuilding | null }>(`/api/settings/${qs({ building_id: buildingId })}`),

  updateProfile: (body: { name?: string; phone?: string }) =>
    request<{ detail: string; user: ApiUser }>('/api/settings/', {
      method: 'PATCH',
      body: { section: 'user', ...body },
    }),

  changePassword: (current_password: string, new_password: string) =>
    request<{ detail: string; token: string }>('/api/settings/', {
      method: 'PATCH',
      body: { section: 'password', current_password, new_password },
    }),

  updateBuilding: (building_id: number, body: Record<string, unknown>) =>
    request<{ detail: string; building: ApiBuilding }>('/api/settings/', {
      method: 'PATCH',
      body: { section: 'building', building_id, ...body },
    }),
}
