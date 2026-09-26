import type {
  AnalyticsOverview,
  ApiAccessCard,
  ApiActivity,
  ApiAsset,
  ApiAssetMaintenance,
  ApiChatRoom,
  ApiDocument,
  ApiDocumentAudit,
  ApiIntercomDevice,
  ApiIntercomLog,
  ApiLiftStatus,
  ApiInvitation,
  ApiListing,
  ApiMessage,
  ApiParkingSlot,
  ApiRentalApplication,
  ApiRentalRequest,
  ApiReview,
  ApiService,
  ApiUtilityBill,
  ApiUtilityMeter,
  ApiVehicle,
  ApiVendor,
  ApiWasteSchedule,
  InvitationPreview,
  IssuedInvitation,
  ParkingLayout,
  PriceEstimate,
  PublicListing,
  RenterSignupPayload,
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
 * The chat socket for a room. Browsers cannot set headers on a WebSocket, so the token rides in the
 * query string; the server validates it during the handshake. Same origin in dev (Vite proxies /ws)
 * and in production (Nginx does), so no absolute host is needed.
 */
export function chatSocketUrl(roomId: number): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws/chat/${roomId}/?token=${encodeURIComponent(getToken() ?? '')}`
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

  /** An account for someone looking for a flat — it belongs to no building until a request is approved. */
  renterSignup: (payload: RenterSignupPayload) =>
    request<AuthResponse>('/api/auth/signup/renter/', { method: 'POST', body: payload, auth: false }),

  logout: () => request<{ detail: string }>('/api/auth/logout/', { method: 'POST' }),

  me: () => request<SessionResponse>('/api/auth/me/'),

  // ---------------------------------------------------------------- registry
  buildings: (page = 1) => request<Page<ApiBuilding>>(`/api/buildings/${qs({ page })}`),

  units: (params: { page?: number; building_id?: number; status?: string; page_size?: number } = {}) =>
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

  // ---------------------------------------------------------------- vendors (Week 5)
  services: () => request<Page<ApiService>>('/api/services/'),

  vendors: (params: { page?: number; building_id?: number; service_id?: number } = {}) =>
    request<Page<ApiVendor>>(`/api/vendors/${qs(params)}`),

  createVendor: (body: {
    building?: number
    service: number
    name: string
    contact_info?: string
    latitude?: string
    longitude?: string
  }) => request<ApiVendor>('/api/vendors/', { method: 'POST', body }),

  nearbyVendors: (params: { service_id: number; lat: number; lng: number; radius_km: number }) =>
    request<{ count: number; results: ApiVendor[] }>(`/api/vendors/nearby/${qs(params)}`),

  reviews: (params: { page?: number; building_id?: number; vendor_id?: number } = {}) =>
    request<Page<ApiReview>>(`/api/reviews/${qs(params)}`),

  createReview: (body: { vendor: number; rating: number; comment?: string }) =>
    request<ApiReview>('/api/reviews/', { method: 'POST', body }),

  // ---------------------------------------------------------------- chat (Week 5)
  chatRooms: (building_id?: number) => request<Page<ApiChatRoom>>(`/api/chat/rooms/${qs({ building_id })}`),

  createChatRoom: (body: { building: number; name: string; is_public: boolean }) =>
    request<ApiChatRoom>('/api/chat/rooms/', { method: 'POST', body }),

  /** Newest page first; the screen reverses it so the stream reads oldest → newest. */
  chatMessages: (room_id: number, page = 1) =>
    request<Page<ApiMessage>>(`/api/chat/messages/${qs({ room_id, page, latest: 'true' })}`),

  sendChatMessage: (room: number, content: string) =>
    request<ApiMessage>('/api/chat/messages/', { method: 'POST', body: { room, content } }),

  // ---------------------------------------------------------------- documents (Week 5)
  documents: (params: { page?: number; building_id?: number; search?: string; is_active?: boolean } = {}) =>
    request<Page<ApiDocument>>(
      `/api/documents/${qs({ ...params, is_active: params.is_active === undefined ? undefined : String(params.is_active) })}`,
    ),

  uploadDocument: (fields: { building: number; title: string; parent?: number }, file: File) => {
    const form = new FormData()
    form.append('building', String(fields.building))
    form.append('title', fields.title)
    if (fields.parent) form.append('parent', String(fields.parent))
    form.append('file', file)
    return upload<ApiDocument>('/api/documents/', form)
  },

  updateDocument: (id: number, body: { title?: string; is_active?: boolean }) =>
    request<ApiDocument>(`/api/documents/${id}/`, { method: 'PATCH', body }),

  downloadDocument: (id: number) => request<{ file_path: string }>(`/api/documents/${id}/download/`),

  documentAudit: (id: number) => request<ApiDocumentAudit[]>(`/api/documents/${id}/audit/`),

  documentVersions: (id: number) => request<ApiDocument[]>(`/api/documents/${id}/versions/`),

  // ---------------------------------------------------------------- parking (Week 5)
  parkingSlots: (building_id?: number) =>
    request<Page<ApiParkingSlot>>(`/api/parking/slots/${qs({ building_id, page_size: 144 })}`),

  parkingLayout: (building_id?: number) => request<ParkingLayout>(`/api/parking/layout/${qs({ building_id })}`),

  generateParkingLayout: (body: { building_id: number; rows: number; columns: number; prefix: string }) =>
    request<{ layout: ParkingLayout; slots: ApiParkingSlot[] }>('/api/parking/layout/', { method: 'POST', body }),

  updateSlot: (id: number, body: { status: string }) =>
    request<ApiParkingSlot>(`/api/parking/slots/${id}/`, { method: 'PATCH', body }),

  vehicles: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiVehicle>>(`/api/vehicles/${qs(params)}`),

  createVehicle: (body: { building?: number; resident?: number; vehicle_number: string; type: string }) =>
    request<ApiVehicle>('/api/vehicles/', { method: 'POST', body }),

  assignVehicle: (id: number, parking_slot: number | null) =>
    request<ApiVehicle>(`/api/vehicles/${id}/`, { method: 'PATCH', body: { parking_slot } }),

  deleteVehicle: (id: number) => request<void>(`/api/vehicles/${id}/`, { method: 'DELETE' }),

  // ---------------------------------------------------------------- facilities (Week 5)
  assets: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiAsset>>(`/api/assets/${qs(params)}`),

  createAsset: (body: Record<string, unknown>) => request<ApiAsset>('/api/assets/', { method: 'POST', body }),

  assetMaintenance: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiAssetMaintenance>>(`/api/asset-maintenance/${qs(params)}`),

  createAssetMaintenance: (body: Record<string, unknown>) =>
    request<ApiAssetMaintenance>('/api/asset-maintenance/', { method: 'POST', body }),

  completeAssetMaintenance: (id: number, completed_date: string) =>
    request<ApiAssetMaintenance>(`/api/asset-maintenance/${id}/`, { method: 'PATCH', body: { completed_date } }),

  liftsCurrent: (building_id?: number) =>
    request<{ results: ApiLiftStatus[] }>(`/api/lifts/current/${qs({ building_id })}`),

  reportLift: (body: { building: number; asset: number | null; status: string }) =>
    request<ApiLiftStatus>('/api/lifts/status/', { method: 'POST', body }),

  wasteSchedules: (building_id?: number) =>
    request<Page<ApiWasteSchedule>>(`/api/waste-schedules/${qs({ building_id })}`),

  createWasteSchedule: (body: { building: number; schedule_time: string; recurring: string }) =>
    request<ApiWasteSchedule>('/api/waste-schedules/', { method: 'POST', body }),

  deleteWasteSchedule: (id: number) => request<void>(`/api/waste-schedules/${id}/`, { method: 'DELETE' }),

  // ---------------------------------------------------------------- utilities (Week 5)
  utilityMeters: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiUtilityMeter>>(`/api/utility-meters/${qs(params)}`),

  createUtilityMeter: (body: { unit: number; type: string; meter_number: string }) =>
    request<ApiUtilityMeter>('/api/utility-meters/', { method: 'POST', body }),

  utilityBills: (params: { page?: number; building_id?: number; status?: string } = {}) =>
    request<Page<ApiUtilityBill>>(`/api/utility-bills/${qs(params)}`),

  updateUtilityBill: (id: number, body: { reading_value?: string; amount?: string }) =>
    request<ApiUtilityBill>(`/api/utility-bills/${id}/`, { method: 'PATCH', body }),

  generateUtilityBills: (building_id: number, month: string) =>
    request<{ created_bills: number[] }>('/api/utility-bills/generate/', {
      method: 'POST',
      body: { building_id, month },
    }),

  // ---------------------------------------------------------------- rentals (Week 5)
  listings: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiListing>>(`/api/listings/${qs(params)}`),

  createListing: (body: Record<string, unknown>) =>
    request<ApiListing>('/api/listings/', { method: 'POST', body }),

  deleteListing: (id: number) => request<void>(`/api/listings/${id}/`, { method: 'DELETE' }),

  rentalRequests: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiRentalRequest>>(`/api/rental-requests/${qs(params)}`),

  requestRental: (listing: number) =>
    request<ApiRentalRequest>('/api/rental-requests/', { method: 'POST', body: { listing } }),

  decideRental: (id: number, status: 'approved' | 'rejected') =>
    request<ApiRentalRequest>(`/api/rental-requests/${id}/`, { method: 'PATCH', body: { status } }),

  withdrawRental: (id: number) => request<void>(`/api/rental-requests/${id}/`, { method: 'DELETE' }),

  updateListing: (id: number, body: Record<string, unknown>) =>
    request<ApiListing>(`/api/listings/${id}/`, { method: 'PATCH', body }),

  // ---------------------------------------------------------------- the public flats page
  publicListings: (params: { page?: number; search?: string; max_rent?: string } = {}) =>
    request<Page<PublicListing>>(`/api/public/listings/${qs(params)}`, { auth: false }),

  publicListing: (id: number) => request<PublicListing>(`/api/public/listings/${id}/`, { auth: false }),

  rentalApplications: () => request<Page<ApiRentalApplication>>('/api/rental-applications/'),

  applyForFlat: (listing: number, message: string) =>
    request<ApiRentalApplication>('/api/rental-applications/', {
      method: 'POST',
      body: { listing, message: message || undefined },
    }),

  withdrawApplication: (id: number) => request<void>(`/api/rental-applications/${id}/`, { method: 'DELETE' }),

  // ---------------------------------------------------------------- invitations
  invitations: (params: { page?: number; building_id?: number; roles?: string } = {}) =>
    request<Page<ApiInvitation>>(`/api/invitations/${qs(params)}`),

  invite: (body: Record<string, unknown>) =>
    request<IssuedInvitation>('/api/invitations/', { method: 'POST', body }),

  renewInvitation: (id: number) => request<IssuedInvitation>(`/api/invitations/${id}/renew/`, { method: 'POST' }),

  revokeInvitation: (id: number) => request<void>(`/api/invitations/${id}/`, { method: 'DELETE' }),

  previewInvitation: (token: string) =>
    request<InvitationPreview>('/api/invitations/preview/', { method: 'POST', body: { token }, auth: false }),

  acceptInvitation: (body: { token: string; password: string; name?: string; phone?: string }) =>
    request<AuthResponse>('/api/invitations/accept/', { method: 'POST', body, auth: false }),

  /**
   * Public (AllowAny) — a cache miss answers 202 with a null estimate rather than an error,
   * which `request` treats as success, so the screen just shows "not available yet".
   */
  priceEstimate: (city: string) =>
    request<PriceEstimate>('/api/ml/price-estimate', { method: 'POST', body: { city }, auth: false }),

  // ---------------------------------------------------------------- security & safety (Week 5)
  intercomDevices: (building_id?: number) =>
    request<Page<ApiIntercomDevice>>(`/api/intercom/devices/${qs({ building_id })}`),

  intercomLogs: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiIntercomLog>>(`/api/intercom/logs/${qs(params)}`),

  createIntercomDevice: (body: { building: number; device_name: string; ip_address: string }) =>
    request<ApiIntercomDevice>('/api/intercom/devices/', { method: 'POST', body }),

  accessCards: (params: { page?: number; building_id?: number } = {}) =>
    request<Page<ApiAccessCard>>(`/api/access-cards/${qs(params)}`),

  updateAccessCard: (id: number, status: string) =>
    request<ApiAccessCard>(`/api/access-cards/${id}/`, { method: 'PATCH', body: { status } }),

  createAccessCard: (body: { resident: number; card_number: string }) =>
    request<ApiAccessCard>('/api/access-cards/', { method: 'POST', body }),

  // ---------------------------------------------------------------- platform (Week 5)
  activity: (params: { page?: number; building_id?: number; entity_type?: string } = {}) =>
    request<Page<ApiActivity>>(`/api/activity-logs/${qs(params)}`),

  /** Multi-building KPIs (spec §8.24) — no trailing slash, as the contract specifies. */
  analyticsOverview: () => request<AnalyticsOverview>('/api/analytics/overview'),
}
