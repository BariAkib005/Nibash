import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Logo } from './ui'
import Icon from './Icon'
import { useAuth } from '../lib/auth'

/**
 * The landing page's header and footer, for the other public pages (the flats pages). Same markup
 * and classes as Landing, so the public site reads as one. Someone signed in without a building — a
 * renter — has no workspace to sign out from, so the header offers it here.
 */
export default function PublicSite({ children }: { children: ReactNode }) {
  const { user, building, logout } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  async function signOut() {
    await logout()
    queryClient.clear()
    navigate('/flats')
  }

  return (
    <div className="site">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>
      <header className="site-header">
        <div className="site-container site-nav">
          <Link to="/" aria-label="Nibash home">
            <Logo />
          </Link>
          <nav aria-label="Main navigation" className="site-links">
            <Link to="/flats">Flats for rent</Link>
            <Link to="/#features">The platform</Link>
          </nav>
          <div className="flex items-center gap-5">
            {user ? (
              <button type="button" onClick={signOut} className="sign-in-link">
                Sign out
              </button>
            ) : (
              <Link to="/login" state={{ from: '/flats/mine' }} className="sign-in-link">
                Sign in
              </Link>
            )}
            <Link to={user && building ? '/app' : user ? '/flats/mine' : '/flats'} className="action-link">
              {user && building ? 'Open dashboard' : user ? 'My requests' : 'Browse flats'}
              <Icon name="arrow" size={16} />
            </Link>
          </div>
        </div>
      </header>
      <main id="main-content">{children}</main>
      <footer className="site-footer site-container">
        <Link to="/" aria-label="Nibash home">
          <Logo />
        </Link>
        <p>A little more order. A little more home.</p>
        <span>© {new Date().getFullYear()} Nibash</span>
      </footer>
    </div>
  )
}
