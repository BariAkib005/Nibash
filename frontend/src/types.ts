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
