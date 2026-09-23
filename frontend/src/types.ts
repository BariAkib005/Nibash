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

// ---------------------------------------------------------------- Week 5: vendors

export interface ApiService {
  id: number
  name: string
  parent: number | null
}

export interface ApiVendor {
  id: number
  service: number
  building: number | null
  name: string
  contact_info: string | null
  rating: string | number | null
  latitude: string | number | null
  longitude: string | number | null
  created_at: string | null
  service_name: string
  /** Only set by the nearby search. */
  distance_km: number | null
}

export interface ApiReview {
  id: number
  vendor: number
  resident: number
  rating: number
  comment: string | null
  created_at: string
  resident_name: string
  vendor_name: string
}

// ---------------------------------------------------------------- chat

export interface ApiChatRoom {
  id: number
  name: string
  is_public: boolean
  building: number
}

export interface ApiMessage {
  id: number
  room: number
  resident: number
  content: string
  sent_at: string
  sender_name: string
  sender_user: number
}

/** Frames pushed by /ws/chat/{roomId}/. */
export type ChatFrame =
  | { type: 'message.created'; message: ApiMessage }
  | { type: 'typing'; sender: string; user_id: number }
  | { type: 'message'; text: string; sender: string }

// ---------------------------------------------------------------- documents

export interface ApiDocument {
  id: number
  building: number
  title: string
  file_path: string
  version: number
  mime_type: string | null
  parent: number | null
  is_active: boolean
  uploaded_by: number
  uploaded_at: string
  uploaded_by_name: string
}

export interface ApiDocumentAudit {
  id: number
  document: number
  user: number
  event_type: 'edit' | 'download' | 'view'
  event_time: string
  user_name: string
}

// ---------------------------------------------------------------- parking

export type SlotStatus = 'available' | 'occupied' | 'reserved'

export interface ApiParkingSlot {
  id: number
  building: number
  slot_number: string
  status: SlotStatus
}

export interface ParkingLayout {
  rows: number
  columns: number
  prefix: string
}

export interface ApiVehicle {
  id: number
  resident: number
  parking_slot: number | null
  vehicle_number: string
  type: 'car' | 'motorbike' | 'bicycle' | 'other'
  registered_at: string
  resident_name: string
  unit_number: string | null
  slot_number: string | null
}

// ---------------------------------------------------------------- facilities

export type WarrantyState = 'active' | 'expiring' | 'expired' | null

export interface ApiAsset {
  id: number
  building: number
  name: string
  type: string
  purchase_date: string | null
  warranty_expiry: string | null
  status: 'operational' | 'under_maintenance' | 'out_of_service' | 'decommissioned'
  warranty_state: WarrantyState
}

export interface ApiAssetMaintenance {
  id: number
  asset: number
  scheduled_date: string
  completed_date: string | null
  description: string | null
  cost: string | number | null
  vendor: number | null
  asset_name: string
  vendor_name: string | null
}

export type LiftStatus = 'operational' | 'maintenance' | 'out_of_order'

export interface ApiLiftStatus {
  id: number
  building: number
  asset: number | null
  status: LiftStatus
  timestamp: string
  name: string
}

export interface ApiWasteSchedule {
  id: number
  building: number
  schedule_time: string
  recurring: 'daily' | 'weekly' | 'biweekly' | 'monthly' | null
  next_occurrence: string | null
}

// ---------------------------------------------------------------- utilities

export interface ApiUtilityMeter {
  id: number
  unit: number
  type: 'electricity' | 'water' | 'gas'
  meter_number: string
  unit_number: string
}

export interface ApiUtilityBill {
  id: number
  meter: number
  reading_date: string
  reading_value: string | number
  amount: string | number
  status: 'pending' | 'billed' | 'paid'
  meter_number: string
  meter_type: ApiUtilityMeter['type']
  unit: number
  unit_number: string
}

// ---------------------------------------------------------------- rentals

export interface ApiListing {
  id: number
  resident: number
  building: number
  unit: number | null
  title: string
  description: string
  rent: string | number
  available_from: string
  created_at: string
  resident_name: string
  unit_number: string | null
  lister_user: number
}

export interface ApiRentalRequest {
  id: number
  listing: number
  tenant: number
  status: 'pending' | 'approved' | 'rejected'
  requested_at: string
  listing_title: string
  tenant_name: string
  tenant_user: number
  lister_user: number
}

export interface PriceEstimate {
  city: string
  estimate: string | number | null
  currency: string
  model_version: string
}

// ---------------------------------------------------------------- security & safety

export interface ApiIntercomDevice {
  id: number
  building: number
  device_name: string
  ip_address: string
}

export interface ApiIntercomLog {
  id: number
  device: number
  event_type: string
  timestamp: string
  details: string | null
  device_name: string
}

export interface ApiAccessCard {
  id: number
  resident: number
  card_number: string
  issued_at: string
  status: 'active' | 'lost' | 'revoked'
  resident_name: string
  unit_number: string | null
}

// ---------------------------------------------------------------- platform

export interface ApiActivity {
  id: number
  user: number
  entity_type: string
  entity_id: number
  action: string
  details_json: string | null
  timestamp: string
  user_name: string
}

export interface BuildingKpis {
  building_id: number
  name: string
  invoices: number
  payments_sum: number
  open_tickets: number
  bookings: number
  occupancy: number
  total_units: number
}

export interface AnalyticsOverview {
  invoices: number
  payments_sum: number
  open_tickets: number
  bookings: number
  occupancy: number
  per_building: BuildingKpis[]
}
