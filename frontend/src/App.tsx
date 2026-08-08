import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import { BuildingProvider } from './lib/building'
import { ToastProvider } from './lib/toast'
import ProtectedRoute from './components/ProtectedRoute'
import AppShell from './components/AppShell'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Signup from './pages/Signup'
import Dashboard from './pages/Dashboard'
import Units from './pages/Units'
import Residents from './pages/Residents'
import Directory from './pages/Directory'
import StaffPage from './pages/StaffPage'
import Settings from './pages/Settings'
import NotFound from './pages/NotFound'

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
                    <AppShell />
                  </BuildingProvider>
                </ProtectedRoute>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path="units" element={<Units />} />
              <Route path="residents" element={<Residents />} />
              <Route path="directory" element={<Directory />} />
              <Route path="staff" element={<StaffPage />} />
              <Route path="settings" element={<Settings />} />
              {/* Modules from Weeks 3–5 mount here; anything else falls back to the dashboard. */}
              <Route path="*" element={<Navigate to="/app" replace />} />
            </Route>

            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
