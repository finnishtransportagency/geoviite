# Vertical geometry diagram demo

A reference implementation for drawing a railway track height (vertical geometry)
diagram from the Geoviite _paikannuspohja v1_ API. The user picks a track number and a
set of its location tracks; the app fetches each track's vertical profile and draws the
height graph with PVI annotations, in the style of Geoviite's own vertical geometry
diagram.

[`vertical_geometry_api_doc.md`](vertical_geometry_api_doc.md) documents the API and
the domain terms (PVI, tangent point, track address, …) used throughout the code.

## From the API to a height at a point

The path from API response to knowing the track height at a given m-value is meant to
be as short as possible to follow:

1. **Endpoints called** — [`src/store/data-slice.ts`](src/store/data-slice.ts), via the
   thin fetch wrapper in [`src/api/client.ts`](src/api/client.ts):
   - `/paikannuspohja/v1/ratanumerot` — track numbers (once, at startup)
   - `/paikannuspohja/v1/toiminnalliset-pisteet` — operational points (once, at startup)
   - `/paikannuspohja/v1/sijaintiraiteet?ratanumero_oid=…` — the location tracks of the
     chosen track number
   - `/paikannuspohja/v1/sijaintiraiteet/{oid}/pystygeometria` — a location track's
     vertical profile (the PVIs); fetched per displayed track
   - `/paikannuspohja/v1/sijaintiraiteet/{oid}/geometria` — a location track's map
     geometry with m-values; used for the track's length and for placing operational
     points
2. **Response types** — [`src/api/types.ts`](src/api/types.ts), field names in Finnish,
   matching the API JSON.
3. **`pviItemsFromProfile`** ([`src/math/profile.ts`](src/math/profile.ts)) flattens
   each profile response into `PviItem`s: one PVI with its vertical curve, in m-values
   along the track.
4. **`profileHeightAtM`** (same file) is the heart of the whole thing: the N2000 height
   of the track at a given m-value — straight grade lines between curves, a parabolic
   approximation of the circular arcs within them.
5. **`buildDiagramTracks`** ([`src/math/diagram-model.ts`](src/math/diagram-model.ts))
   combines the responses of all displayed tracks and links PVIs across track
   boundaries, so a short track with no PVIs of its own still gets its height from the
   grade line continuing in from its neighbours.
6. **Rendering** — [`src/components/diagram.tsx`](src/components/diagram.tsx) maps
   m-values and heights to pixels ([`src/math/coordinates.ts`](src/math/coordinates.ts))
   and draws the profile by sampling `profileHeightAtM` once per pixel
   ([`src/components/height-graph.tsx`](src/components/height-graph.tsx)), plus the PVI
   annotations ([`src/components/track-pvi-geometry.tsx`](src/components/track-pvi-geometry.tsx)).

## Embedding

The diagram proper (`Diagram` in
[`src/components/diagram.tsx`](src/components/diagram.tsx)) takes everything it needs as
props and does not touch the Redux store. Its sizes and positions are centralized in
`DiagramDimensions` ([`src/math/coordinates.ts`](src/math/coordinates.ts)) and can be
overridden through the `Diagram` component's `dimensions` prop to fit a host site.

The surrounding UI (track number search, address range, location track list) is
demo scaffolding: the eventual plan is to replace it with a routing API that returns
the list of location tracks along a route.

## Running

```sh
npm install
npm run dev    # dev server; proxies API calls, see vite.config.ts
npm test       # unit tests for the math
npm run build  # type-check and produce dist/
```

The backends do not allow CORS from the dev server origin, so `vite.config.ts` proxies
the environments under `/local`, `/dev`, `/test` and `/prod`; the environment chosen in
the app's settings picks the proxy path. `local` expects a Geoviite backend at
`http://localhost:8080`; the others need an API key, entered in the app.
