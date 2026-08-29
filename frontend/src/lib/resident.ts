import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'
import { useBuilding } from './building'

/**
 * The caller's own resident row in the building they are viewing.
 *
 * <p>Several actions are taken *as a resident* rather than as a user — raising an SOS, casting a
 * vote — and the API keys those on a resident id, not a user id. The session payload carries the
 * user, so this resolves the missing half.
 *
 * <p>Returns {@code undefined} for anyone with no resident row here, which is a real case: a guard
 * or a back-office admin is attached to the building as staff. Callers must handle it rather than
 * assume, and the screens do — the SOS button explains itself instead of failing.
 */
export function useCurrentResident() {
  const { user } = useAuth()
  const { currentId } = useBuilding()

  const { data, isLoading } = useQuery({
    queryKey: ['residents', currentId, 'me'],
    queryFn: () => api.residents({ building_id: currentId }),
    enabled: Boolean(user && currentId),
    staleTime: 5 * 60 * 1000,
  })

  const resident = data?.results.find((row) => row.user === user?.id)
  return { resident, residentId: resident?.id, loading: isLoading }
}
