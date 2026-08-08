import { useEffect } from 'react'

/**
 * Reveals every `.reveal` element as it scrolls into view. One observer for the whole page —
 * cheaper than a hook per section, and it re-scans when `deps` change.
 */
export function useReveal(): void {
  useEffect(() => {
    const elements = document.querySelectorAll<HTMLElement>('.reveal')

    // No IntersectionObserver (or reduced motion) → just show everything.
    if (typeof IntersectionObserver === 'undefined') {
      elements.forEach((el) => el.classList.add('is-visible'))
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible')
            observer.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    )

    elements.forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [])
}
