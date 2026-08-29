import type {
  ApiAppointment,
  ApiAttendance,
  ApiAttendee,
  ApiBillType,
  ApiBooking,
  ApiEmergencyContact,
  ApiEvent,
  ApiExpense,
  ApiGateEvent,
  ApiInvoice,
  ApiNotice,
  ApiNotification,
  ApiPoll,
  ApiResource,
  ApiTicket,
  ApiTicketImage,
  ApiVisitor,
  CheckoutResponse,
  GateAnalyticsBucket,
  MonthlyExpenseRow,
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

/**
 * Multipart sibling of {@link request}, for the two endpoints that take a file.
 *
 * <p>The Content-Type header is deliberately *not* set: the browser has to add it itself so it can
 * append the multipart boundary. Setting it by hand produces a body the server cannot parse.
 */
async function upload<T>(path: string, form: FormData): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Token ${token}`

  const response = await fetch(path, { method: 'POST', headers, body: form })
  if (response.status === 401) clearToken()

  const text = await response.text()
  const payload = text ? safeParse(text) : null

  if (!response.ok) throw toApiError(response.status, payload)
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

  // ---------------------------------------------------------------- finance (Week 3)
  invoices: (params: {
    page?: number
    building_id?: number
    resident_id?: number
    status?: string
    due_before?: string
    due_after?: string
  } = {}) => request<Page<ApiInvoice>>(`/api/invoices/${qs(params)}`),

  invoice: (id: number) => request<ApiInvoice>(`/api/invoices/${id}/`),

  createInvoice: (body: Record<string, unknown>) =>
    request<ApiInvoice>('/api/invoices/', { method: 'POST', body }),

  generateMonthly: (body: {
    building_id: number
    bill_type_id: number
    billing_month: string
    due_date: string
    include_utilities?: boolean
  }) => request<{ created_invoices: number[] }>('/api/invoices/generate-monthly/', { method: 'POST', body }),

  remindInvoice: (id: number) =>
    request<{ detail: string }>(`/api/invoices/${id}/remind/`, { method: 'POST' }),

  billTypes: () => request<Page<ApiBillType>>('/api/bill-types/'),

  checkout: (invoice_id: number, method = 'card') =>
    request<CheckoutResponse>('/api/payments/checkout/', { method: 'POST', body: { invoice_id, method } }),

  expenses: (params: { page?: number; building_id?: number; category?: string } = {}) =>
    request<Page<ApiExpense>>(`/api/expenses/${qs(params)}`),

  createExpense: (body: Record<string, string | number | undefined>, receipt?: File | null) => {
    const form = new FormData()
    for (const [key, value] of Object.entries(body)) {
      if (value !== undefined && value !== '') form.append(key, String(value))
    }
    if (receipt) form.append('receipt', receipt)
    return upload<ApiExpense>('/api/expenses/', form)
  },

  deleteExpense: (id: number) => request<void>(`/api/expenses/${id}/`, { method: 'DELETE' }),

  expenseReport: (building_id?: number) =>
    request<{ results: MonthlyExpenseRow[] }>(`/api/expenses/reports/monthly/${qs({ building_id })}`),

  // ---------------------------------------------------------------- maintenance (Week 3)
  tickets: (params: { page?: number; building_id?: number; status?: string; resident_id?: number } = {}) =>
    request<Page<ApiTicket>>(`/api/tickets/${qs(params)}`),

  ticket: (id: number) => request<ApiTicket>(`/api/tickets/${id}/`),

  createTicket: (body: Record<string, unknown>) =>
    request<ApiTicket>('/api/tickets/', { method: 'POST', body }),

  updateTicket: (id: number, body: Record<string, unknown>) =>
    request<ApiTicket>(`/api/tickets/${id}/`, { method: 'PATCH', body }),

  setTicketStatus: (id: number, status: string) =>
    request<ApiTicket>(`/api/tickets/${id}/status/`, { method: 'PATCH', body: { status } }),

  addTicketImage: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return upload<ApiTicketImage>(`/api/tickets/${id}/images/`, form)
  },

  attendance: (params: { page?: number; building_id?: number; staff_id?: number } = {}) =>
    request<Page<ApiAttendance>>(`/api/attendance/${qs(params)}`),

  checkin: (staff_id: number) =>
    request<ApiAttendance>('/api/attendance/checkin/', { method: 'POST', body: { staff_id } }),

  checkoutShift: (staff_id: number) =>
    request<ApiAttendance>('/api/attendance/checkout/', { method: 'POST', body: { staff_id } }),

  // ---------------------------------------------------------------- visitors (Week 4)
  appointments: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiAppointment>>(`/api/appointments/${qs(params)}`),

  createAppointment: (body: Record<string, unknown>) =>
    request<ApiAppointment>('/api/appointments/', { method: 'POST', body }),

  deleteAppointment: (id: number) => request<void>(`/api/appointments/${id}/`, { method: 'DELETE' }),

  visitors: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiVisitor>>(`/api/visitors/${qs(params)}`),

  scanVisitor: (qr_token: string) =>
    request<ApiVisitor>('/api/visitors/scan/', { method: 'POST', body: { qr_token } }),

  visitorCheckin: (id: number) =>
    request<ApiVisitor>(`/api/visitors/${id}/checkin/`, { method: 'PATCH', body: {} }),

  visitorCheckout: (id: number) =>
    request<ApiVisitor>(`/api/visitors/${id}/checkout/`, { method: 'PATCH', body: {} }),

  // ---------------------------------------------------------------- gate & alerts (Week 4)
  gateEvents: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiGateEvent>>(`/api/gate-events/${qs(params)}`),

  logGateEvent: (building: number, event_type: 'open' | 'close') =>
    request<ApiGateEvent>('/api/gate-events/', { method: 'POST', body: { building, event_type } }),

  gateAnalytics: (building_id?: number) =>
    request<{ results: GateAnalyticsBucket[] }>(`/api/gate-events/analytics/${qs({ building_id })}`),

  raiseSos: (resident: number, coords?: { latitude: number; longitude: number }) =>
    request<{ id: number }>('/api/emergencies/', { method: 'POST', body: { resident, ...coords } }),

  notifications: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiNotification>>(`/api/notifications/${qs(params)}`),

  unreadCount: (building_id?: number) =>
    request<{ unread: number }>(`/api/notifications/unread-count/${qs({ building_id })}`),

  markNotificationRead: (id: number) =>
    request<ApiNotification>(`/api/notifications/${id}/`, { method: 'PATCH', body: { is_read: true } }),

  emergencyContacts: (building_id?: number) =>
    request<Page<ApiEmergencyContact>>(`/api/emergency-contacts/${qs({ building_id })}`),

  // ---------------------------------------------------------------- community (Week 4)
  notices: (params: { page?: number; building_id?: number; search?: string; include_archived?: boolean } = {}) =>
    request<Page<ApiNotice>>(
      `/api/notices/${qs({
        page: params.page,
        building_id: params.building_id,
        search: params.search,
        include_archived: params.include_archived ? 'true' : undefined,
      })}`,
    ),

  createNotice: (body: Record<string, unknown>) =>
    request<ApiNotice>('/api/notices/', { method: 'POST', body }),

  updateNotice: (id: number, body: Record<string, unknown>) =>
    request<ApiNotice>(`/api/notices/${id}/`, { method: 'PATCH', body }),

  deleteNotice: (id: number) => request<void>(`/api/notices/${id}/`, { method: 'DELETE' }),

  polls: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiPoll>>(`/api/polls/${qs(params)}`),

  createPoll: (body: Record<string, unknown>) =>
    request<ApiPoll>('/api/polls/', { method: 'POST', body }),

  vote: (pollId: number, option_id: number, resident_id: number) =>
    request<{ id: number }>(`/api/polls/${pollId}/vote/`, { method: 'POST', body: { option_id, resident_id } }),

  events: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiEvent>>(`/api/events/${qs(params)}`),

  createEvent: (body: Record<string, unknown>) =>
    request<ApiEvent>('/api/events/', { method: 'POST', body }),

  eventAttendees: (eventId: number) =>
    request<{ event_id: number; results: ApiAttendee[] }>(`/api/events/${eventId}/attendees/`),

  rsvp: (eventId: number, status: string) =>
    request<ApiAttendee>(`/api/events/${eventId}/rsvp/`, { method: 'POST', body: { status } }),

  // ---------------------------------------------------------------- bookings (Week 4)
  resources: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiResource>>(`/api/resources/${qs(params)}`),

  createResource: (body: Record<string, unknown>) =>
    request<ApiResource>('/api/resources/', { method: 'POST', body }),

  availability: (resourceId: number, start_from: string, end_to: string) =>
    request<{ resource_id: number; bookings: ApiBooking[] }>(
      `/api/resources/${resourceId}/availability/${qs({ start_from, end_to })}`,
    ),

  bookings: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiBooking>>(`/api/bookings/${qs(params)}`),

  createBooking: (body: Record<string, unknown>) =>
    request<ApiBooking>('/api/bookings/', { method: 'POST', body }),

  /** Preflight for the calendar: same rules, nothing written (spec §8.8). */
  quoteBooking: (body: Record<string, unknown>) =>
    request<{ available: boolean; estimated_fee: string | number }>('/api/bookings/quote/', {
      method: 'POST',
      body,
    }),

  cancelBooking: (id: number) =>
    request<ApiBooking>(`/api/bookings/${id}/`, { method: 'PATCH', body: { status: 'cancelled' } }),
}
