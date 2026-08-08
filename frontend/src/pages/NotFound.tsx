import { Link } from 'react-router-dom'
import { Button, Logo } from '../components/ui'

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-6 text-center">
      <Logo />
      <p className="mt-8 text-sm font-bold uppercase tracking-widest text-brand-700">404</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-900">Page not found</h1>
      <p className="mt-2 max-w-sm text-slate-600">
        That page doesn't exist — it may have moved, or it may be part of a module arriving in a
        later week of the build.
      </p>
      <Link to="/" className="mt-8">
        <Button>Back to home</Button>
      </Link>
    </div>
  )
}
