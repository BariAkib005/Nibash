import { Link } from 'react-router-dom'
import { Logo } from '../components/ui'
import Icon from '../components/Icon'
import type { IconName } from '../components/Icon'
import BuildingIllustration from '../components/BuildingIllustration'
import { useAuth } from '../lib/auth'

const modules: { icon: IconName; title: string; body: string; detail: string }[] = [
  {
    icon: 'wallet',
    title: 'Finances, in order.',
    body: 'Keep service charges, invoices and expenses together. Give every payment a clear paper trail.',
    detail: 'Invoices & expense tracking',
  },
  {
    icon: 'shield',
    title: 'A more considered welcome.',
    body: 'Expect a visitor, share a QR pass and keep a record of who comes through the gate.',
    detail: 'Visitor passes & gate logs',
  },
  {
    icon: 'tool',
    title: 'Small fixes. Proper follow-through.',
    body: 'Report an issue with a photo and follow it from the first request to the final repair.',
    detail: 'Maintenance & staff',
  },
  {
    icon: 'notice',
    title: 'Keep everyone in the loop.',
    body: 'Give building updates a home. Share notices, take a community vote and plan your next gathering.',
    detail: 'Notices, polls & events',
  },
  {
    icon: 'calendar',
    title: 'Shared spaces, less back-and-forth.',
    body: 'See when a facility is free and reserve a time, with booking conflicts checked before you confirm.',
    detail: 'Facilities & bookings',
  },
  {
    icon: 'people',
    title: 'Know your community.',
    body: 'Keep units, residents and staff organised, with a directory that respects contact-sharing preferences.',
    detail: 'Registry & resident directory',
  },
]

export default function Landing() {
  const { user } = useAuth()
  const destination = user ? '/app' : '/signup'
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
            <a href="#features">The platform</a>
            <a href="#community">Who it’s for</a>
            <Link to="/flats">Flats for rent</Link>
          </nav>
          <div className="flex items-center gap-5">
            {!user && (
              <Link to="/login" className="sign-in-link">
                Sign in
              </Link>
            )}
            <Link to={destination} className="action-link">
              {user ? 'Open dashboard' : 'Get started'}
              <Icon name="arrow" size={16} />
            </Link>
          </div>
        </div>
      </header>
      <main id="main-content">
        <section className="site-container hero">
          <div className="hero-copy">
            <p className="eyebrow">
              <span className="eyebrow-line" /> A little more harmony at home
            </p>
            <h1>
              Better managed.
              <br />
              Better <em>living.</em>
            </h1>
            <p className="hero-description">
              A well-run building makes room for a better everyday. Bring your finances, maintenance and
              community together with Nibash.
            </p>
            <div className="hero-actions">
              <Link to={destination} className="action-link action-link-large">
                {user ? 'Go to your dashboard' : 'Set up your building'}
                <Icon name="arrow" size={18} />
              </Link>
              <a href="#features" className="text-link">
                Explore the platform <span aria-hidden="true">↘</span>
              </a>
            </div>
            <div className="hero-footnote">
              <Icon name="check" size={16} /> One place for everyone in your building.
            </div>
          </div>
          <div className="hero-visual">
            <div className="visual-label">
              <span>THE EVERYDAY, SIMPLIFIED</span>
              <span>01 / NIBASH</span>
            </div>
            <BuildingIllustration />
            <div className="visual-note">
              <span className="visual-note-icon">
                <Icon name="building" size={22} />
              </span>
              <div>
                <strong>A place for your building.</strong>
                <span>And everyone who calls it home.</span>
              </div>
            </div>
            <span className="visual-caption">Thoughtfully connected. Effortlessly organised.</span>
          </div>
        </section>
        <div className="platform-strip">
          <div className="site-container">
            <span>
              Made for life
              <br />
              <strong>in your building.</strong>
            </span>
            <p>
              <Icon name="building" /> Multiple buildings, one workspace
            </p>
            <p>
              <Icon name="people" /> Five roles, a shared purpose
            </p>
            <p>
              <Icon name="wallet" /> Built for Bangladesh · BDT
            </p>
          </div>
        </div>
        <section id="features" className="site-container features-section">
          <div className="section-intro">
            <div>
              <p className="eyebrow">LESS ADMIN. MORE LIVING.</p>
              <h2>
                Everything that keeps
                <br />a building running.
              </h2>
            </div>
            <p>
              From the monthly service charge to the rooftop booking. The everyday details, finally in one
              place.
            </p>
          </div>
          <div className="feature-grid">
            {modules.map((module, i) => (
              <article className="feature" key={module.title}>
                <div className="feature-top">
                  <Icon name={module.icon} size={25} />
                  <span>0{i + 1}</span>
                </div>
                <h3>{module.title}</h3>
                <p>{module.body}</p>
                <span className="feature-detail">{module.detail}</span>
              </article>
            ))}
          </div>
        </section>
        <section id="community" className="community-section">
          <div className="site-container community-grid">
            <div>
              <p className="eyebrow">A CONNECTED COMMUNITY</p>
              <h2>
                One building.
                <br />
                Many perspectives.
              </h2>
              <p>
                A useful workspace for each person, with the right tools for their part in keeping things
                running.
              </p>
              <Link to={destination} className="text-link">
                Bring your building together <Icon name="arrow" size={18} />
              </Link>
            </div>
            <div className="role-list">
              {[
                ['01', 'Administrators', 'The whole picture. Buildings, people and operations.'],
                ['02', 'Committees', 'Clear finances and a more informed community.'],
                ['03', 'Residents', 'Your bills, requests, visitors and shared spaces.'],
                ['04', 'Security teams', 'A simpler way to welcome and check in visitors.'],
                ['05', 'Building staff', 'Assigned maintenance and daily attendance.'],
              ].map(([number, title, body]) => (
                <div key={number}>
                  <span>{number}</span>
                  <section>
                    <h3>{title}</h3>
                    <p>{body}</p>
                  </section>
                  <Icon name="check" size={18} />
                </div>
              ))}
            </div>
          </div>
        </section>
        <section className="site-container closing-section">
          <p className="eyebrow">WELCOME TO NIBASH</p>
          <h2>
            A better everyday
            <br />
            starts with your building.
          </h2>
          <Link to={destination} className="action-link action-link-large">
            {user ? 'Open your workspace' : 'Create your workspace'}
            <Icon name="arrow" size={18} />
          </Link>
          <p>Set up your building. Choose your modules. Make it yours.</p>
        </section>
      </main>
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
