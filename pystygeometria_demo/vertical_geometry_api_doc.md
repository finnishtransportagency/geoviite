# Vertical Geometry API Documentation

## Glossary

| Finnish                   | English                              | Description                                                                                                                                                                                                                                                                                                           |
| ------------------------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| sijaintiraide             | location track                       | A track with geometry corresponding to a physical railway track in the world.                                                                                                                                                                                                                                         |
| ratanumero                | track number                         | Usually a single longer physical track. Has 0..n location tracks. Also has a reference line geometry that defines geocoding and track addresses.                                                                                                                                                                      |
| rataosoite                | track address                        | Format: `0623+0931.105` — kilometer number before the `+`, meter distance within that kilometer after it. Track kilometers have arbitrary lengths: they are defined by km posts, which were initially placed a physical kilometer apart along the reference line but the reference line's shape may change over time. |
| paaluluku                 | m-value (chainage)                   | Distance along a track geometry from its start point, in meters.                                                                                                                                                                                                                                                      |
| taitepiste                | point of vertical intersection (PVI) | The theoretical point where two adjacent tangent grade lines intersect. The vertical curve rounds off this intersection.                                                                                                                                                                                              |
| kaltevuusjakso            | profile section                      | The section between two consecutive PVIs (or between the track start/end and the nearest PVI). Contains both the straight portion and the adjacent curved portions.                                                                                                                                                   |
| pyöristysjakso            | vertical curve section               | The circular arc that smooths the transition between two grade lines at a PVI.                                                                                                                                                                                                                                        |
| kaltevuus                 | slope (grade)                        | Rise per unit of horizontal distance. E.g. −0.007 means the track drops 7 mm per meter (−0.7%).                                                                                                                                                                                                                       |
| pyöristyksen alku / loppu | tangent points                       | Where the circular curve begins / ends — the points where the curve meets the straight grade lines.                                                                                                                                                                                                                   |
| pyöristyssäde             | vertical curve radius                | Signed radius of the circular arc at a PVI.                                                                                                                                                                                                                                                                           |
| tangentti                 | tangent length                       | Distance from the PVI to each of its tangent points (equal on both sides).                                                                                                                                                                                                                                            |

---

## Understanding the Vertical Profile

The vertical profile describes how the rail height (N2000) varies along a track. It consists of:

1. **Straight grade sections** — the track rises or falls at a constant slope.
2. **Circular arc curves** — smooth transitions at each PVI where two grade lines meet.

The profile is a 2D function with m-value on the horizontal axis and N2000 height on the vertical axis.

Key points:

- The **PVI is not on the actual profile**. It is the theoretical intersection of two adjacent grade lines. The actual track surface passes above (sag curve) or below (crest curve) the PVI.
- The **tangent points** mark where each curve begins and ends. At these points, the curve smoothly joins the straight grade lines — the slope is continuous.
- The **tangent length** (`tangentti`) is the distance from the PVI to each tangent point, and is equal on both sides.
- Each **straight section** between adjacent curves has a single constant slope.

---

## JSON Response Structure

### Top-level fields

| Field               | Description                                                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `rataverkon_versio` | UUID identifying the version of the track network data.                                                            |
| `sijaintiraide_oid` | OID of the location track whose vertical geometry is described.                                                    |
| `koordinaatisto`    | Coordinate reference system used for `x`/`y` positions in `sijainti` fields. Typically `EPSG:3067` (ETRS-TM35FIN). |
| `osoitevali`        | Address range covered by this response, containing the PVI data.                                                   |

### Address range (`osoitevali`)

| Field          | Description                             |
| -------------- | --------------------------------------- |
| `alku`         | Start track address of the range.       |
| `loppu`        | End track address of the range.         |
| `taitepisteet` | Array of PVI entries within this range. |

### PVI entry (`taitepiste`)

Each entry in the `taitepisteet` array describes one PVI and the vertical curve that rounds it off:

| Field                             | Description                                                                                                                                                   |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `pyoristyksen_alku`               | **Curve start tangent point** — where the circular curve begins. Has height, slope, and position.                                                             |
| `taite`                           | **PVI point** — the theoretical intersection of the two tangent grade lines. Has height and position, but no slope (it is not a point on the actual profile). |
| `pyoristyksen_loppu`              | **Curve end tangent point** — where the circular curve ends. Has height, slope, and position.                                                                 |
| `pyoristyssade`                   | **Curve radius** — signed radius of the circular arc in meters. Positive for upward-turning, negative for downward-turning ones.                              |
| `tangentti`                       | **Tangent length** — distance from the PVI to each tangent point, in meters.                                                                                  |
| `kaltevuusjakso_taaksepain`       | **Backward profile section** — describes the section from the previous PVI _in the design plan_ to this PVI. See the plan-relative caveats below.             |
| `kaltevuusjakso_eteenpain`        | **Forward profile section** — describes the section from this PVI to the next PVI _in the design plan_. See the plan-relative caveats below.                  |
| `paaluluku`                       | **M-values** — chainage values for the tangent start, PVI, and tangent end. **Each may be `null`** for points beyond the track's ends (see below).            |
| `suunnitelman_korkeusjarjestelma` | Height system used in the original design plan (e.g. `N2000`, `N60`).                                                                                         |
| `suunnitelman_korkeusasema`       | Height reference within the track cross-section (e.g. `Korkeusviiva` = rail top).                                                                             |
| `huomiot`                         | Remarks or notes (array of objects with `koodi` and `selite`).                                                                                                |

### Tangent point fields (`pyoristyksen_alku` / `pyoristyksen_loppu`)

| Field                  | Description                                                                                           |
| ---------------------- | ----------------------------------------------------------------------------------------------------- |
| `korkeus_n2000`        | Height in the N2000 height system. **Use this for calculations.**                                     |
| `korkeus_alkuperainen` | Height in the original plan's height system. Equals `korkeus_n2000` when the plan already uses N2000. |
| `kaltevuus`            | Slope of the straight grade line at this tangent point (rise/run, e.g. −0.007050 = −0.705% downhill). |
| `sijainti.x`           | Easting in the coordinate system specified by `koordinaatisto`.                                       |
| `sijainti.y`           | Northing in the coordinate system specified by `koordinaatisto`.                                      |
| `sijainti.rataosoite`  | Track address at this point.                                                                          |

### PVI point fields (`taite`)

Same as tangent point fields, but **without `kaltevuus`** — the PVI is a theoretical intersection point, not a point on the actual profile, so it has no meaningful slope.

### M-value fields (`paaluluku`)

| Field   | Description                               |
| ------- | ----------------------------------------- |
| `alku`  | M-value at the curve start tangent point. |
| `taite` | M-value at the PVI.                       |
| `loppu` | M-value at the curve end tangent point.   |

These are distances in meters along the track geometry from its start point (m = 0).

**Each of these fields can be `null`.** The profile comes from design plans whose
geometry can extend beyond the location track's ends, and m-values exist only for points
on the track itself. When a tangent point or PVI falls outside the track, its m-value is
`null` — while the point's `sijainti` (with a track address beyond the `osoitevali`
range) is still reported. This typically affects the first or last PVI of a track.
Consumers must handle PVIs whose station values are partially or entirely `null`; note
in particular that sorting or arithmetic on a `null` m-value silently treats it as 0 in
JavaScript.

Note: when all three values are present, `taite − alku` and `loppu − taite` both approximately
equal the `tangentti` value.

### Profile section fields (`kaltevuusjakso_taaksepain` / `kaltevuusjakso_eteenpain`)

| Field       | Description                                                                                                                      |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `pituus`    | Total length of this section — the distance from the previous PVI to this PVI (backward) or from this PVI to the next (forward). |
| `suora_osa` | Length of the straight portion within this section.                                                                              |

The difference `pituus − suora_osa` equals the combined length of the curved portions at each end of the section.

**Section lengths are plan-relative, not track-relative.** They are measured in the
design plan's geometry, which leads to two caveats:

- A PVI's `kaltevuusjakso_eteenpain` matches the next PVI's `kaltevuusjakso_taaksepain`
  only when both PVIs come from the same design plan. At a plan boundary (often visible
  as a change in `suunnitelman_korkeusasema` or `suunnitelman_korkeusjarjestelma`), each
  section runs only to its own plan's boundary: the two lengths do not match each other,
  and neither equals the m-distance between the PVIs.
- The first PVI's backward section and the last PVI's forward section run to the
  adjacent PVI in the plan, which may lie before the track's start or beyond its end.
  They therefore do not reliably span to m = 0 or to the track's end — do not derive the
  track's m-extent from them.

---

## Calculating N2000 Height at a Given M-value

Given the API response, follow these steps to compute the N2000 height at any m-value along the track.

### Determining the segment type

The profile is divided into alternating straight and curved segments. For the example JSON with 3 PVIs:

| Segment | From                              | To                                | Type     | Slope / Data source                                                          |
| ------- | --------------------------------- | --------------------------------- | -------- | ---------------------------------------------------------------------------- |
| 1       | 0                                 | PVI 1 `paaluluku.alku` (49.449)   | Straight | PVI 1 `pyoristyksen_alku.kaltevuus`                                          |
| 2       | PVI 1 `paaluluku.alku` (49.449)   | PVI 1 `paaluluku.loppu` (78.268)  | Curved   | PVI 1 curve data                                                             |
| 3       | PVI 1 `paaluluku.loppu` (78.268)  | PVI 2 `paaluluku.alku` (298.665)  | Straight | PVI 1 `pyoristyksen_loppu.kaltevuus` (= PVI 2 `pyoristyksen_alku.kaltevuus`) |
| 4       | PVI 2 `paaluluku.alku` (298.665)  | PVI 2 `paaluluku.loppu` (338.527) | Curved   | PVI 2 curve data                                                             |
| 5       | PVI 2 `paaluluku.loppu` (338.527) | PVI 3 `paaluluku.alku` (717.552)  | Straight | PVI 2 `pyoristyksen_loppu.kaltevuus` (= PVI 3 `pyoristyksen_alku.kaltevuus`) |
| 6       | PVI 3 `paaluluku.alku` (717.552)  | PVI 3 `paaluluku.loppu` (758.281) | Curved   | PVI 3 curve data                                                             |
| 7       | PVI 3 `paaluluku.loppu` (758.281) | End of track                      | Straight | PVI 3 `pyoristyksen_loppu.kaltevuus`                                         |

### Height on a straight segment

On a straight segment, height changes linearly. Use the adjacent tangent point:

```
h(m) = h_ref + slope × (m − m_ref)
```

Where `h_ref`, `slope`, and `m_ref` come from the tangent point at the boundary of this segment:

- For the segment **before** a curve: use that curve's start tangent (`pyoristyksen_alku`).
- For the segment **after** a curve: use that curve's end tangent (`pyoristyksen_loppu`).

Specifically:

- `h_ref` = `korkeus_n2000` of the tangent point
- `slope` = `kaltevuus` of the tangent point
- `m_ref` = `paaluluku.alku` or `paaluluku.loppu` respectively

### Height on a curved segment

#### Parabolic approximation (recommended for most uses)

For railway slopes (typically well under 5%), a parabolic approximation of the circular arc is accurate to sub-millimeter precision and is much simpler to implement:

```
h(m) = h₁ + s₁ × (m − m₁) + (m − m₁)² / (2 × R)
```

Where:

- `m₁` = `paaluluku.alku` — m-value at the curve start tangent point
- `h₁` = `pyoristyksen_alku.korkeus_n2000` — height at the curve start tangent point
- `s₁` = `pyoristyksen_alku.kaltevuus` — slope at the curve start tangent point
- `R` = `pyoristyssade` - radius

You can verify the formula gives the correct height at the curve end tangent point (`paaluluku.loppu`, `pyoristyksen_loppu.korkeus_n2000`).

#### Exact circular arc (what the Geoviite codebase uses)

The Geoviite implementation in `GeometryProfile.kt` uses exact circular arc geometry. The process is:

**1. Compute the arc's center point** from the start tangent point and the PVI:

```
θ = atan2(h_tangent − h_pvi, m_tangent − m_pvi)
φ = θ − π/2

center_m = m_tangent + R × cos(φ)
center_h = h_tangent + R × sin(φ)
```

Where `(m_tangent, h_tangent)` is the curve start tangent point and `(m_pvi, h_pvi)` is the PVI point.

**2. Compute the height** on the arc at a given m-value:

```
α = asin((m − center_m) / |R|)
h(m) = center_h − R × cos(α)
```

In practice, the parabolic approximation gives results identical to the exact circular arc at sub-millimeter accuracy for typical railway grades. Use the exact method only if you need to match the Geoviite implementation precisely.

---

## Worked Examples

### Example 1: Height on a straight segment (m = 200)

**Step 1:** m = 200 falls in the straight segment between PVI 1's end tangent (m = 78.268) and PVI 2's start tangent (m = 298.665).

**Step 2:** Use PVI 1's end tangent point as the reference:

- `m_ref` = 78.268
- `h_ref` = 80.567 (`pyoristyksen_loppu.korkeus_n2000`)
- `slope` = −0.000500 (`pyoristyksen_loppu.kaltevuus`)

```
h(200) = 80.567 + (−0.000500) × (200 − 78.268)
       = 80.567 + (−0.000500) × 121.732
       = 80.567 − 0.061
       = 80.506 m (N2000)
```

### Example 2: Height on a curved segment (m = 55)

**Step 1:** m = 55 falls in the curved segment of PVI 1 (m = 49.449 to 78.268).

**Step 3:** The radius is `pyoristyssade` = **+4400** (positive = sag curve, which makes sense since the slope increases from −0.705% to −0.05%).

Using the parabolic approximation:

- `m₁` = 49.449, `h₁` = 80.676, `s₁` = −0.007050

```
h(55) = 80.676 + (−0.007050) × (55 − 49.449) + (55 − 49.449)² / (2 × 4400)
      = 80.676 + (−0.007050) × 5.551  + 5.551² / 8800
      = 80.676 − 0.039  + 0.004
      = 80.641 m (N2000)
```
