import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { formatDate, taka } from '../lib/format'
import PublicSite from '../components/PublicSite'
import { Button, EmptyState, Field, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import type { PublicListing } from '../types'

/**
 * The public flats page: every flat a Nibash building has chosen to publish and not yet let. No
 * account needed to browse; asking for one happens on the flat's own page.
 */
export default function Flats() {
  const [search, setSearch] = useState('')
  const [maxRent, setMaxRent] = useState('')
  const [applied, setApplied] = useState({ search: '', max_rent: '' })
  const [page, setPage] = useState(1)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['public-listings', applied, page],
    queryFn: () => api.publicListings({ ...applied, page }),
    placeholderData: keepPreviousData,
  })

  const badRent = maxRent !== '' && !(Number(maxRent) > 0)

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    if (badRent) return
    setPage(1)
    setApplied({ search: search.trim(), max_rent: maxRent.trim() })
  }

  const results = data?.results ?? []
  const filtered = Boolean(applied.search || applied.max_rent)

  return (
    <PublicSite>
      <section className="site-container features-section">
        <div className="section-intro">
          <div>
            <p className="eyebrow">
              <span className="eyebrow-line" /> FLATS FOR RENT
            </p>
            <h2>
              Find a place
              <br />
              to call home.
            </h2>
          </div>
          <p>
            Flats let directly by buildings on Nibash. Ask for one and the building replies here — once they say yes,
            you’re a resident from day one.
          </p>
        </div>

        <form onSubmit={handleSearch} className="mb-10 grid gap-3 sm:grid-cols-[1fr_200px_auto] sm:items-end">
          <Field
            label="Area, building or keyword"
            placeholder="Gulshan, lake view, 3BHK…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <Field
            label="Max rent (৳ / month)"
            inputMode="numeric"
            placeholder="Any"
            value={maxRent}
            onChange={(e) => setMaxRent(e.target.value)}
            error={badRent ? 'Enter an amount' : undefined}
          />
          <Button type="submit" className="py-2.5">
            Search
          </Button>
        </form>

        {isLoading ? (
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-60 w-full" />)}
          </div>
        ) : isError ? (
          <EmptyState icon="alert" title="Couldn’t load the flats" body="Please try again in a moment." />
        ) : results.length ? (
          <>
            <p className="mb-4 text-xs text-slate-500">
              {data?.count} flat{data?.count === 1 ? '' : 's'} {filtered ? 'match your search' : 'available now'}
            </p>
            <div className="feature-grid">
              {results.map((listing) => (
                <FlatCard key={listing.id} listing={listing} />
              ))}
            </div>
            {(data?.next || data?.previous) && (
              <div className="mt-8 flex items-center justify-between">
                <button type="button" className="text-link" disabled={!data?.previous} onClick={() => setPage((p) => p - 1)}>
                  <span aria-hidden="true">←</span> Newer
                </button>
                <span className="text-xs text-slate-500">Page {page}</span>
                <button type="button" className="text-link" disabled={!data?.next} onClick={() => setPage((p) => p + 1)}>
                  Older <span aria-hidden="true">→</span>
                </button>
              </div>
            )}
          </>
        ) : (
          <EmptyState
            icon="home"
            title={filtered ? 'Nothing matches that search' : 'No flats listed right now'}
            body={
              filtered
                ? 'Try a wider area or a higher rent.'
                : 'Buildings add flats here as they come free. Check back soon.'
            }
            action={
              filtered ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSearch('')
                    setMaxRent('')
                    setApplied({ search: '', max_rent: '' })
                  }}
                >
                  Clear search
                </Button>
              ) : undefined
            }
          />
        )}
      </section>
    </PublicSite>
  )
}

function FlatCard({ listing }: { listing: PublicListing }) {
  const facts = [
    listing.unit_type,
    listing.size_sqft ? `${Number(listing.size_sqft).toLocaleString('en-US')} sq ft` : null,
    listing.floor != null ? `floor ${listing.floor}` : null,
  ].filter(Boolean)

  return (
    <article className="feature flex flex-col">
      <div className="feature-top">
        <Icon name="home" size={25} />
        <span>FROM {formatDate(listing.available_from).toUpperCase()}</span>
      </div>
      <h3>
        <Link to={`/flats/${listing.id}`} className="hover:underline hover:underline-offset-4">
          {listing.title}
        </Link>
      </h3>
      <strong className="mt-3 block text-xl font-semibold tracking-tight text-slate-900">
        {taka(listing.rent)}
        <span className="ml-1 text-xs font-normal tracking-normal text-slate-500">/ month</span>
      </strong>
      <p className="line-clamp-3">{listing.description}</p>
      <span className="feature-detail">
        {listing.building_name}
        {facts.length ? ` · ${facts.join(' · ')}` : ''}
      </span>
      <Link to={`/flats/${listing.id}`} className="text-link mt-5">
        See the flat <Icon name="arrow" size={16} />
      </Link>
    </article>
  )
}
