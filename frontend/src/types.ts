/** Shapes returned by the API. Field names are snake_case, matching the contract exactly. */

export type Role = 'admin' | 'committee' | 'resident' | 'guard' | 'staff'

export interface ApiUser {
  id: number
  name: string
  email: string
  phone: string | null
  role: Role
  avatar_path: string | null
  is_listed?: boolean
  address?: string | null
  bio?: string | null
  created_at?: string
}

export interface ApiBuilding {
  id: number
  name: string
  address: string
  developer: number | null
  primary_contact: number | null
  year_built: number | null
  num_floors: number | null
  total_units: number | null
  website: string | null
  amenities_json: string | null
  photo_path: string | null
  created_at: string
}

export type UnitStatus = 'available' | 'occupied' | 'sold' | 'rented'

export interface ApiUnit {
  id: number
  building: number
  unit_number: string
  floor: number | null
  type: string
  size_sqft: string | number | null
  price: string | number | null
  status: UnitStatus
  created_at: string
}

export interface ApiResident {
  id: number
  user: number
  building: number
  unit: number | null
  is_owner: boolean
  opt_in: boolean
  start_date: string | null
  end_date: string | null
  created_at: string
  resident_name: string
  resident_email: string
  unit_number: string | null
}

export interface ApiStaff {
  id: number
  user: number | null
  name: string
  role: string
  designation: string | null
  qualifications: string | null
  building: number
  contact_info: string | null
}

/** Contact fields are null unless the resident opted in (spec §8.1). */
export interface DirectoryEntry {
  id: number
  name: string
  unit: number | null
  unit_number: string | null
  building: number
  email: string | null
  phone: string | null
  avatar_path: string | null
  is_owner: boolean
  opt_in: boolean
}

export interface DirectoryResponse {
  count: number
  results: DirectoryEntry[]
}

/** The DRF envelope on every plain list endpoint, page size 20 (spec §13.1). */
export interface Page<T> {
  count: number
  next: string | null
  previous: string | null
  results: T[]
}

export interface AuthResponse {
  token: string
  user: ApiUser
  building: ApiBuilding | null
}

export interface SessionResponse {
  user: ApiUser
  building: ApiBuilding | null
}

export interface SignupPayload {
  name: string
  email: string
  password: string
  building_name?: string
  modules?: string[]
}

/* ---------------------------------------------------------------- Week 3: finance */

export type InvoiceStatus = 'pending' | 'paid' | 'overdue'

export interface ApiInvoiceItem {
  id: number
  invoice: number
  description: string
  quantity: string | number
  unit_price: string | number
  tax_amount: string | number
  total_amount: string | number
  utility_bill_id: number | null
}

export interface ApiInvoice {
  id: number
  invoice_number: string
  resident: number
  building: number
  bill_type: number | null
  amount: string | number
  due_date: string
  status: InvoiceStatus
  created_at: string
  updated_at: string
  items: ApiInvoiceItem[]
  resident_name: string
  unit_number: string | null
}

export interface ApiBillType {
  id: number
  name: string
  description: string | null
}

export interface ApiPayment {
  id: number
  invoice: number
  resident: number
  amount: string | number
  payment_date: string
  method: string
  transaction_id: string | null
  invoice_number: string
}

export interface CheckoutResponse {
  checkout_status: string
  transaction_id: string
  payment: ApiPayment
}

export interface ApiExpense {
  id: number
  building: number
  category: string
  amount: string | number
  description: string | null
  date: string
  receipt_path: string | null
  created_by: number
  vendor: number | null
  created_at: string
  created_by_name: string
}

export interface MonthlyExpenseRow {
  month: string
  category: string
  total: string | number
  entries: number
}

/* ---------------------------------------------------------------- Week 3: maintenance */

export type TicketStatus = 'open' | 'in_progress' | 'resolved' | 'closed'
export type TicketPriority = 'low' | 'medium' | 'high'

export interface ApiTicketImage {
  id: number
  ticket: number
  image_path: string
}

export interface ApiTicket {
  id: number
  building: number
  resident: number
  category: string
  description: string
  status: TicketStatus
  priority: TicketPriority
  assigned_to: number | null
  service_vendor: number | null
  created_at: string
  updated_at: string
  closed_at: string | null
  images: ApiTicketImage[]
  resident_name: string
  assigned_to_name: string | null
  unit_number: string | null
}

export interface ApiAttendance {
  id: number
  staff: number
  staff_name: string
  checkin_time: string
  checkout_time: string | null
}

/* ---------------------------------------------------------------- Week 4: security */

export interface ApiAppointment {
  id: number
  building: number
  resident: number
  visitor_name: string
  visitor_phone: string
  scheduled_time: string
  approved: boolean
  qr_token: string | null
  created_at: string
  resident_name: string
  unit_number: string | null
}

export type VisitorStatus = 'pending' | 'checked_in' | 'checked_out'

export interface ApiVisitor {
  id: number
  appointment: number
  checkin_time: string | null
  checkout_time: string | null
  status: VisitorStatus
  handled_by: number | null
  visitor_name: string
  visitor_phone: string
  scheduled_time: string
  resident_name: string
  unit_number: string | null
}

export interface ApiGateEvent {
  id: number
  building: number
  event_type: 'open' | 'close'
  timestamp: string
  actor: number | null
  actor_name: string | null
}

export interface GateAnalyticsBucket {
  hour: number
  event_type: 'open' | 'close'
  total: number
}

export interface ApiNotification {
  id: number
  building: number
  resident: number | null
  type: string
  message: string
  sent_at: string
  is_read: boolean
}

export interface ApiEmergencyContact {
  id: number
  building: number
  name: string
  phone: string
  type: string
}

/* ---------------------------------------------------------------- Week 4: community */

export interface ApiNotice {
  id: number
  building: number
  title: string
  body: string
  is_pinned: boolean
  publish_date: string
  expiry_date: string | null
  created_by: number
  created_at: string
  created_by_name: string
}

export interface ApiPollOption {
  id: number
  poll: number
  option_text: string
  votes: number
  percentage: string | number
}

export interface ApiPoll {
  id: number
  building: number
  question: string
  created_by: number
  start_date: string
  end_date: string | null
  is_closed: boolean
  total_votes: number
  options: ApiPollOption[]
}

export type RsvpStatus = 'interested' | 'going' | 'not_going'

export interface ApiEvent {
  id: number
  building: number
  title: string
  description: string | null
  event_date: string
  created_by: number
  created_by_name: string
}

export interface ApiAttendee {
  id: number
  event: number
  resident: number
  status: RsvpStatus
  resident_name: string
}

/* ---------------------------------------------------------------- Week 4: bookings */

export interface ApiResource {
  id: number
  name: string
  capacity: number
  location: string | null
  building: number
  type: string | null
}

export type BookingStatus = 'pending' | 'confirmed' | 'cancelled'

export interface ApiBooking {
  id: number
  resource: number
  resident: number
  start_time: string
  end_time: string
  status: BookingStatus
  purpose: string | null
  created_at: string
  resource_name: string
  resident_name: string
}
