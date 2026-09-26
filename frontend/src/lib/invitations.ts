import type { InvitableRole } from '../types'

/** How each invitable role reads to people — the invite dialog, the pending list and the join page. */
export const ROLE_LABEL: Record<InvitableRole, string> = {
  resident: 'Resident',
  committee: 'Committee member',
  guard: 'Security guard',
  staff: 'Staff',
}
