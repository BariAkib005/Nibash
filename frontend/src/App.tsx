import { Suspense, lazy } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import { BuildingProvider } from './lib/building'
import { ToastProvider } from './lib/toast'
import ProtectedRoute from './components/ProtectedRoute'
import AppShell from './components/AppShell'
import { Skeleton } from './components/ui'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Signup from './pages/Signup'
import Dashboard from './pages/Dashboard'
import NotFound from './pages/NotFound'

/**
 * Everything behind the login is lazily loaded (plan §0.5).
 *
 * <p>The landing page is the first thing a prospect sees and it must not carry the whole app —
 * least of all the camera-scanning library, which only the guard's gate screen ever needs and which
 * is one of the largest dependencies in the project.
 */
const Units = lazy(() => import('./pages/Units'))
const Residents = lazy(() => import('./pages/Residents'))
const Directory = lazy(() => import('./pages/Directory'))
const StaffPage = lazy(() => import('./pages/StaffPage'))
const Invoices = lazy(() => import('./pages/Invoices'))
const Expenses = lazy(() => import('./pages/Expenses'))
const Tickets = lazy(() => import('./pages/Tickets'))
const Notices = lazy(() => import('./pages/Notices'))
const Polls = lazy(() => import('./pages/Polls'))
const Events = lazy(() => import('./pages/Events'))
const Bookings = lazy(() => import('./pages/Bookings'))
const Appointments = lazy(() => import('./pages/Appointments'))
const Visitors = lazy(() => import('./pages/Visitors'))
const GuardScan = lazy(() => import('./pages/GuardScan'))
const GateLog = lazy(() => import('./pages/GateLog'))
const Settings = lazy(() => import('./pages/Settings'))
const Chat = lazy(() => import('./pages/Chat'))
const Parking = lazy(() => import('./pages/Parking'))
const Documents = lazy(() => import('./pages/Documents'))
const Vendors = lazy(() => import('./pages/Vendors'))
const Facilities = lazy(() => import('./pages/Facilities'))
const Utilities = lazy(() => import('./pages/Utilities'))
const Rentals = lazy(() => import('./pages/Rentals'))
const Safety = lazy(() => import('./pages/Safety'))
const Portfolio = lazy(() => import('./pages/Portfolio'))
const Activity = lazy(() => import('./pages/Activity'))
const MyShift = lazy(() => import('./pages/MyShift'))

/** Shown while a route chunk downloads — a skeleton, never a spinner on a blank page. */
function RouteFallback() {
  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-4 w-80" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            {/* public */}
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />

            {/* authenticated — BuildingProvider needs a session, so it lives inside the guard */}
            <Route
              path="/app"
              element={
                <ProtectedRoute>
                  <BuildingProvider>
                    <Suspense fallback={<RouteFallback />}>
                      <AppShell />
                    </Suspense>
                  </BuildingProvider>
                </ProtectedRoute>
              }
            >
              <Route index element={<Dashboard />} />

              {/* registry (Week 2) */}
              <Route path="units" element={<Units />} />
              <Route path="residents" element={<Residents />} />
              <Route path="directory" element={<Directory />} />
              <Route path="staff" element={<StaffPage />} />

              {/* finance & maintenance (Week 3) */}
              <Route path="invoices" element={<Invoices />} />
              <Route path="expenses" element={<Expenses />} />
              <Route path="tickets" element={<Tickets />} />

              {/* security, community & bookings (Week 4) */}
              <Route path="notices" element={<Notices />} />
              <Route path="polls" element={<Polls />} />
              <Route path="events" element={<Events />} />
              <Route path="bookings" element={<Bookings />} />
              <Route path="appointments" element={<Appointments />} />
              <Route path="visitors" element={<Visitors />} />
              <Route path="scan" element={<GuardScan />} />
              <Route path="gate" element={<GateLog />} />

              {/* realtime, facilities & the long tail (Week 5) */}
              <Route path="chat" element={<Chat />} />
              <Route path="parking" element={<Parking />} />
              <Route path="documents" element={<Documents />} />
              <Route path="vendors" element={<Vendors />} />
              <Route path="facilities" element={<Facilities />} />
              <Route path="utilities" element={<Utilities />} />
              <Route path="rentals" element={<Rentals />} />
              <Route path="safety" element={<Safety />} />
              <Route path="portfolio" element={<Portfolio />} />
              <Route path="activity" element={<Activity />} />
              <Route path="shift" element={<MyShift />} />

              <Route path="settings" element={<Settings />} />

              {/* Anything else falls back to the dashboard. */}
              <Route path="*" element={<Navigate to="/app" replace />} />
            </Route>

            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
