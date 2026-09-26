# Nibash — demo script

The five journeys from the plan (§7), click by click, against the seeded demo data, plus a sixth: a
brand-new building bringing its people in. Every demo account uses the password **`Nibash@2026`**.
About 20 minutes end to end.

**Before you start**

- Start the stack: `docker compose up -d` (with `NIBASH_SEED=true` the first time) → http://localhost:8080,
  or run the backend with `--seed` and the Vite dev server → http://127.0.0.1:5173.
- Use two browser windows (one normal, one private) for the chat step and whenever two roles meet.
- The seeded visitor pass is dated the day the seeder ran. On a later day, create a fresh pass in
  journey 2 — a pass only opens the gate on its own date.

---

## 1. Admin — money moves

Sign in as **`admin1@nibash.bd`** (Imran Chowdhury).

1. **Overview** — the building's units, occupancy and neighbours.
2. **Portfolio** — both buildings side by side: occupancy, collected, open tickets. Click
   *Banani Garden Square* to switch building; click Portfolio again to come back.
3. **Invoices → Generate month** — pick next month and *Service Charge*, tick *include utilities*, run it.
   *Expect:* one invoice per resident, with each unit's pending electricity and water bills as line items.
4. Run it again for the same month. *Expect:* `0 created` — the batch is idempotent.
5. **Utilities** — the bills that were just invoiced now show *billed*, so next month cannot charge them again.
6. **Expenses → Record expense** — category *Generator Fuel*, an amount, today, attach a receipt photo or PDF.
   *Expect:* it appears in the list and the stacked month-by-category chart grows.
7. **Activity** — the billing run and any payment appear with who did them.

## 2. Resident — daily life

Sign in as **`resident1@nibash.bd`** (Ayesha Rahman).

1. **Invoices** — Ayesha's own invoices. *Pay* a pending one. *Expect:* it flips to *paid*.
2. **Bookings** — drag two hours for the *Rooftop Lounge* and book. Drag an overlapping slot.
   *Expect:* refused before you submit.
3. **Polls** — vote once. Vote again. *Expect:* *Already voted*.
4. **Expected visitors → Expect a visitor** — today, any time. *Expect:* a QR pass with its code.
   Keep the code for journey 3.
5. **Maintenance → Raise ticket** — category *Maintenance*. *Expect:* auto-assigned to Kamal Sheikh, whose staff role is Maintenance.
6. **Chat** — open *General*; in the private window sign in as `committee1@nibash.bd` and open Chat too.
   Type as Ayesha. *Expect:* "Ayesha Rahman is typing…" on the other side, then the message, live.
   Ayesha does not see the private *Committee* room; Farhana does.
7. **Parking** — the bay map; Ayesha's car is in *G1-01*. *Register vehicle* adds another under her name.
8. **Vendors → Find someone nearby** — *Plumbing*, 5 km: one plumber. 10 km: the Mirpur plumber appears
   too, further away. Open *Reviews* on *Dhaka Lift Care* and leave a star rating.
9. **Documents** — download the *Building bylaws*; open *History*. *Expect:* your download in the audit trail.
10. **Rentals** — the rent guide shows about ৳72,000 for Dhaka; *Request to rent* the flat on floor 3.

## 3. Guard — the gate

Sign in as **`guard1@nibash.bd`** (Jamal Uddin) — on a phone if you can.

1. **Gate scan** — scan the QR from journey 2 (or type its code). *Expect:* checked in.
   Scan it again. *Expect:* still checked in, arrival time unchanged.
2. **Visitor log** — the visitor is there; *Check out*.
3. **Gate log** — *Gate opened*, then *Gate closed*. *Expect:* the hourly traffic chart moves.
4. **Facilities** — a lift is stuck? Tap *Out of order* on Lift B. Tap *Running* when it's fixed.
5. **Safety & access** — the emergency numbers (tap to call) and the intercom log.

## 4. Staff — the shift

Sign in as **`staff1@nibash.bd`** (Ruma Begum).

1. **My shift** (in the sidebar for staff and guards, built for a phone) — *Check in*.
   *Expect:* "On shift" with the start time. The server is idempotent: a second check-in (e.g. from a
   second phone) returns the same open shift instead of starting another.
2. *Check out*. *Expect:* "Off shift", and the shift listed with its duration.
3. Managers see everyone's shifts on **Staff → Attendance** and can check people in and out there.

## 5. Committee — the noticeboard and SOS

Sign in as **`committee1@nibash.bd`** (Farhana Haque).

1. **Notices → New notice** — pinned. Sign in as `resident1` in the other window:
   *Expect:* it tops the notice board.
2. As `resident1`, hold the red **SOS** button. *Expect:* an unmistakable confirmation, and a new
   *sos* notification in the bell for the building.
3. Back as Farhana: **Rentals** — Farhana listed the floor-3 flat, so Ayesha's request is waiting.
   *Approve* it.
4. **Documents → New version** of the *Annual budget 2026*. *Expect:* version 3; version 2 is kept in *History*.
5. **Facilities → Schedule service** for Lift B, and note the warranty warning for Lift B and the generator.

## 6. A new building — from sign-up to its first tenant

Use one normal window (the owner) and one private window (everyone else, one at a time).

1. **Sign up** a new workspace (any email, a building name). *Expect:* an empty building, you as its admin.
2. **Units → Add unit** twice, say 5A and 5B.
3. **Staff → Invite staff** — *Security guard*, a name and any email → *Create invitation*.
   *Expect:* a one-time link. Copy it (without SMTP the screen says to send it yourself).
4. In the private window, open the link. *Expect:* “Join <your building>”. Choose a password → you are
   the guard, in the building. **My shift → Check in** works, and so would Gate scan.
   Open the same link again. *Expect:* “already been used”.
5. Back as the owner: **Residents → Invite resident** into flat 5A the same way. The *Waiting to join*
   list offers *New link* (the old one stops working) and *Withdraw*.
6. **Rentals → List a flat** — flat 5B, *Show it on the public flats page* ticked. *Expect:* a *Public* badge.
7. In the private window (signed out), open **/flats**. *Expect:* your flat, and the seeded
   *Sunny top-floor 3BHK — 06B* from Gulshan. Open yours → *New to Nibash* → create an account →
   write a note → *Send request*. **My requests** shows it *Waiting for the building*.
8. As the owner: **Rentals** → the request is marked *From outside*, with the renter's email, phone and
   note → *Approve*.
9. As the renter: refresh **My requests**. *Expect:* “You're a resident of <building>” → *Open your
   building* → the renter's workspace. Flat 5B is now *rented* on Units and gone from /flats.

---

## Things worth pointing out

- **Tenancy:** sign in as `resident1` and open `/api/buildings/2/` — a `404`, not a `403`: the other
  building is not even acknowledged. (`admin1`/`admin2` are back-office and see both buildings on purpose.)
- **Concurrency:** two simultaneous checkouts of one invoice write exactly one payment; two simultaneous
  bookings of one slot leave exactly one winner — both are pinned by the test suite.
- **Live chat** runs over a WebSocket whose handshake checks the session token.
- **Invitation links** keep their token in the `#fragment`, so it never reaches a server log; only its
  hash is stored. And a resident lister can decline an outsider, but only the committee can let one in.
- **One call** — `GET /api/dashboard/summary/` returns 8 metrics and 32 sections, with a statement
  count that stays flat as the data grows (tested).
