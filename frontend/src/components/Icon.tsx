import type { SVGProps } from 'react'

const paths = {
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  building: 'M4 21V5l8-3v19 M12 8h8v13 M2 21h20 M7 7v1 M7 11v1 M7 15v1 M16 11h1 M16 15h1 M8 21v-3h4',
  people:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M17 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
  directory: 'M4 3h15v18H4z M2 7h4 M2 12h4 M2 17h4 M9 16h6 M14 9a2 2 0 1 0-4 0 2 2 0 0 0 4 0',
  wallet: 'M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h16v12H5a3 3 0 0 1-3-3V6 M21 12h-6v5h6 M17 14.5h.01',
  receipt: 'M5 3l3 2 4-2 4 2 3-2v18l-3-2-4 2-4-2-3 2z M9 9h6 M9 13h6',
  tool: 'M14 6a5 5 0 0 0-6 6L3 17a3 3 0 0 0 4 4l5-5a5 5 0 0 0 6-6l-3 3-4-4 3-3z',
  notice: 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h4',
  poll: 'M4 20V10h4v10 M10 20V4h4v16 M16 20v-7h4v7 M2 20h20',
  calendar: 'M4 5h16v16H4z M8 3v4 M16 3v4 M4 10h16 M8 14h2 M14 14h2 M8 17h2',
  visitor:
    'M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 0-8 0 4 4 0 0 0 8 0 M16 10h6 M19 7l3 3-3 3',
  shield: 'M12 3l9 4v5c0 5-9 9-9 9s-9-4-9-9V7z M8 12l3 3 5-6',
  scan: 'M8 3H3v5 M16 3h5v5 M21 16v5h-5 M8 21H3v-5 M7 7h3v3H7z M14 7h3v3h-3z M7 14h3v3H7z M14 14h3v3h-3z',
  gate: 'M3 21V3h5v18 M16 21V3h5v18 M8 7h8 M8 12h8 M8 17h8',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',
  arrow: 'M4 12h16 M14 6l6 6-6 6',
  chevron: 'M9 5l7 7-7 7',
  check: 'M5 12l4 4L19 6',
  menu: 'M3 6h18 M3 12h18 M3 18h18',
  close: 'M6 6l12 12 M6 18L18 6',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  alert: 'M12 3L2 21h20L12 3z M12 9v5 M12 17h.01',
  logout: 'M9 3H3v18h6 M9 12h12 M16 7l5 5-5 5',
  box: 'M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 5 9-5 M12 12v9',
  chat: 'M21 12a8 8 0 0 1-11.6 7.1L3 21l1.9-6.4A8 8 0 1 1 21 12z M8 12h.01 M12 12h.01 M16 12h.01',
  car: 'M5 17h14 M3 17v-5l2-5h14l2 5v5 M3 12h18 M7 17v2 M17 17v2 M7 14.5h.01 M17 14.5h.01',
  file: 'M14 3H6v18h12V7z M14 3v4h4 M9 12h6 M9 16h6',
  store: 'M4 10v11h16V10 M3 4h18l-1 6H4z M9 21v-6h6v6',
  lift: 'M5 3h14v18H5z M12 3v18 M7.5 9.5L9 7.5l1.5 2 M13.5 14.5l1.5 2 1.5-2',
  bolt: 'M13 2L4 14h7l-1 8 9-12h-7z',
  home: 'M3 11l9-7 9 7 M5 10v10h14V10 M10 20v-5h4v5',
  layers: 'M12 3l9 4.5-9 4.5-9-4.5z M3 12l9 4.5 9-4.5 M3 16.5l9 4.5 9-4.5',
  phone: 'M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2z',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  star: 'M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3 6.5 20.2l1-6.2L3 9.6l6.2-.9z',
  download: 'M12 4v11 M7 10l5 5 5-5 M5 20h14',
  upload: 'M12 20V9 M7 14l5-5 5 5 M5 4h14',
  plus: 'M12 5v14 M5 12h14',
  send: 'M21 3L10 14 M21 3l-7 18-4-8-8-4z',
  pin: 'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5',
  history: 'M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5 M12 7v5l3 3',
  trash: 'M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13',
  card: 'M3 6h18v12H3z M3 10h18 M7 15h4',
  lock: 'M6 11h12v10H6z M8 11V7a4 4 0 0 1 8 0v4',
  copy: 'M9 9h11v11H9z M5 15H4V4h11v1',
  mail: 'M3 5h18v14H3z M3 6l9 7 9-7',
} as const

export type IconName = keyof typeof paths
export default function Icon({
  name,
  size = 20,
  ...props
}: SVGProps<SVGSVGElement> & { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  )
}
