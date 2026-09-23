import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Logo } from './ui'
import Icon from './Icon'
import BuildingIllustration from './BuildingIllustration'

export default function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="auth-layout">
      <div className="auth-form-side">
        <header>
          <Link to="/" aria-label="Nibash home">
            <Logo />
          </Link>
          <Link to="/" className="auth-back">
            Back to home <Icon name="arrow" size={16} />
          </Link>
        </header>
        <main className="auth-form">
          <p className="eyebrow">YOUR BUILDING. YOUR COMMUNITY.</p>
          <h1>{title}</h1>
          <p className="auth-subtitle">{subtitle}</p>
          <div className="mt-8">{children}</div>
          {footer && <p className="auth-footer">{footer}</p>}
        </main>
        <p className="auth-copyright">© {new Date().getFullYear()} Nibash · Made for everyday living.</p>
      </div>
      <aside className="auth-brand-side">
        <div>
          <p className="eyebrow">A PLACE FOR EVERYTHING.</p>
          <h2>
            Less to manage.
            <br />
            More to call <em>home.</em>
          </h2>
          <p>
            One shared space for a well-run building
            <br />
            and a better-connected community.
          </p>
        </div>
        <BuildingIllustration />
        <span className="auth-brand-caption">
          FINANCES &nbsp; / &nbsp; MAINTENANCE &nbsp; / &nbsp; COMMUNITY
        </span>
      </aside>
    </div>
  )
}
