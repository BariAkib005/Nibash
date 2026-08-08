import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import { useAuth } from './auth'
import type { ApiBuilding } from '../types'

const STORAGE_KEY = 'nibash.buildingId'

interface BuildingState {
  /** Every building the caller may see — the tenancy boundary, straight from the API. */
  buildings: ApiBuilding[]
  current: ApiBuilding | null
  currentId: number | undefined
  select: (id: number) => void
  loading: boolean
}

const BuildingContext = createContext<BuildingState | null>(null)

export function BuildingProvider({ children }: { children: ReactNode }) {
  const { user, building: homeBuilding } = useAuth()
  const [selectedId, setSelectedId] = useState<number | null>(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored ? Number(stored) : null
  })

  const { data, isLoading } = useQuery({
    queryKey: ['buildings'],
    queryFn: () => api.buildings(),
    enabled: Boolean(user),
  })

  const buildings = useMemo(() => data?.results ?? [], [data])

  // Fall back to the home building whenever the stored selection isn't in the allowed set —
  // which is exactly what happens when you sign in as a different tenant on the same browser.
  const current = useMemo(() => {
    if (!buildings.length) return homeBuilding
    return (
      buildings.find((b) => b.id === selectedId) ??
      buildings.find((b) => b.id === homeBuilding?.id) ??
      buildings[0]
    )
  }, [buildings, selectedId, homeBuilding])

  useEffect(() => {
    if (current?.id) localStorage.setItem(STORAGE_KEY, String(current.id))
  }, [current?.id])

  const select = useCallback((id: number) => setSelectedId(id), [])

  const value = useMemo<BuildingState>(
    () => ({ buildings, current, currentId: current?.id, select, loading: isLoading }),
    [buildings, current, select, isLoading],
  )

  return <BuildingContext value={value}>{children}</BuildingContext>
}

export function useBuilding(): BuildingState {
  const context = useContext(BuildingContext)
  if (!context) throw new Error('useBuilding must be used inside <BuildingProvider>')
  return context
}
