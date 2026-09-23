/** Local architectural artwork, with no remote asset requests. */
export default function BuildingIllustration({ className = '' }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 600 640"
      fill="none"
      role="img"
      aria-label="An apartment building framed by trees in a quiet courtyard"
    >
      <rect width="600" height="640" fill="#e8e9df" />
      <circle cx="473" cy="132" r="72" fill="#ded3b9" />
      <path d="M0 510L600 458V640H0Z" fill="#d3d6c8" />
      <path d="M84 640L380 461H432L257 640Z" fill="#eee9dc" />
      <path d="M155 156L346 107L483 163V510L302 557L155 505Z" fill="#c7c5b6" />
      <path d="M155 156L346 107V509L155 505Z" fill="#eee9dc" />
      <path d="M346 107L483 163V510L346 552Z" fill="#b6b9a8" />
      <path d="M145 153L344 101L494 160L483 174L345 121L155 169Z" fill="#f5f1e7" />
      {[0, 1, 2, 3, 4].map((row) => (
        <g key={row}>
          {[0, 1, 2].map((col) => (
            <g key={col}>
              <path d={`M${180 + col * 51} ${191 + row * 61 - col * 13}l30-8v42l-30 8z`} fill="#5b7167" />
              <path
                d={`M${195 + col * 51} ${187 + row * 61 - col * 13}v42`}
                stroke="#dddccf"
                strokeWidth="2"
              />
              <path d={`M${175 + col * 51} ${234 + row * 61 - col * 13}l40-10v7l-40 10z`} fill="#d1cbbb" />
            </g>
          ))}
          {[0, 1].map((col) => (
            <g key={col}>
              <path d={`M${368 + col * 54} ${167 + row * 64 + col * 21}l32 13v39l-32-13z`} fill="#4d665c" />
              <path
                d={`M${384 + col * 54} ${173 + row * 64 + col * 21}v40`}
                stroke="#a7b1a0"
                strokeWidth="2"
              />
              <path d={`M${361 + col * 54} ${208 + row * 64 + col * 21}l47 18v8l-47-18z`} fill="#d9d6c7" />
            </g>
          ))}
        </g>
      ))}
      <path d="M239 488l59-16v71l-59-19z" fill="#304e42" />
      <path d="M268 481v54" stroke="#b2bcaa" strokeWidth="2" />
      <path d="M221 481l78-22 22 9-79 23z" fill="#faf4e5" />
      <path d="M215 530l88 26 35-10-86-27z" fill="#b7b9a6" />
      <path d="M90 516V326 M517 534V344" stroke="#677663" strokeWidth="9" />
      <ellipse cx="91" cy="321" rx="64" ry="106" fill="#849781" />
      <ellipse cx="73" cy="300" rx="39" ry="76" fill="#92a189" />
      <ellipse cx="520" cy="352" rx="59" ry="99" fill="#647f6c" />
      <ellipse cx="504" cy="330" rx="38" ry="73" fill="#768e75" />
      <path d="M90 443v-87l-22-26 M517 458v-94l24-31" stroke="#526d58" strokeWidth="3" />
      <ellipse cx="146" cy="510" rx="51" ry="24" fill="#748c70" />
      <ellipse cx="458" cy="525" rx="45" ry="25" fill="#819473" />
      <path d="M36 574h121 M455 578h106" stroke="#bac1af" strokeWidth="2" />
      <path d="M398 556v27 M420 550v27 M391 559l37-10 M391 567l37-10" stroke="#6e7968" strokeWidth="4" />
      <circle cx="338" cy="563" r="7" fill="#b57e5f" />
      <path
        d="M338 572v25 M338 579l-10 11 M338 579l9 7 M338 597l-6 16 M338 597l7 16"
        stroke="#38594b"
        strokeWidth="6"
        strokeLinecap="round"
      />
    </svg>
  )
}
