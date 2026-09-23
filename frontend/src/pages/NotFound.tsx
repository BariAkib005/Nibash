import { Link } from 'react-router-dom'
import { Logo } from '../components/ui'
import Icon from '../components/Icon'

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <Logo />
      <p className="mt-8 text-sm font-bold uppercase tracking-widest text-brand-700">404</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">Page not found</h1>
      <p className="mt-2 max-w-sm text-slate-600">
        We couldn’t find that page. Head back home to find your way to your building.
      </p>
      <Link to="/" className="action-link mt-8">
        Back to home <Icon name="arrow" size={16} />
      </Link>
    </div>
  )
}
