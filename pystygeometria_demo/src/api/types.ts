// Types for the Geoviite paikannuspohja v1 API responses (field names are Finnish,
// matching the API JSON).

export interface ExtAddressPoint {
  x: number;
  y: number;
  rataosoite: string;
}

export interface ExtTrackNumber {
  ratanumero_oid: string;
  ratanumero: string;
  kuvaus: string;
  tila: string;
  alkusijainti?: ExtAddressPoint;
  loppusijainti?: ExtAddressPoint;
}

export interface ExtTrackNumberCollectionResponse {
  rataverkon_versio: string;
  koordinaatisto: string;
  ratanumerot: ExtTrackNumber[];
}

export interface CommonData {
  trackNumbers: ExtTrackNumber[];
  operationalPoints: ExtOperationalPoint[];
}

export interface ExtOperationalPoint {
  toiminnallinen_piste_oid: string;
  nimi: string;
  lyhenne?: string;
  sijainti?: { x: number; y: number };
}

export interface ExtOperationalPointCollectionResponse {
  toiminnalliset_pisteet: ExtOperationalPoint[];
}

export type LocationTrackType =
  | "pääraide"
  | "sivuraide"
  | "kujaraide"
  | "turvaraide";

export const allLocationTrackTypes: readonly LocationTrackType[] = [
  "pääraide",
  "sivuraide",
  "kujaraide",
  "turvaraide",
];

export interface ExtLocationTrack {
  sijaintiraide_oid: string;
  sijaintiraidetunnus: string;
  ratanumero: string;
  ratanumero_oid: string;
  tyyppi: LocationTrackType;
  tila: string;
  kuvaus: string;
  omistaja: string;
  alkusijainti?: ExtAddressPoint;
  loppusijainti?: ExtAddressPoint;
}

export interface ExtLocationTrackCollectionResponse {
  rataverkon_versio: string;
  koordinaatisto: string;
  sijaintiraiteet: ExtLocationTrack[];
}

export interface ExtProfileCurvedSectionEndpoint {
  korkeus_alkuperainen: number;
  korkeus_n2000: number;
  kaltevuus: number;
  sijainti: ExtAddressPoint;
}

export interface ExtProfileIntersectionPoint {
  korkeus_alkuperainen: number;
  korkeus_n2000: number;
  sijainti: ExtAddressPoint;
}

export interface ExtProfileLinearSection {
  pituus: number;
  suora_osa: number;
}

// Station values are null for points that fall outside the location track: the profile
// comes from design plans whose geometry can extend past the track's ends, and m-values
// only exist on the track itself.
export interface ExtProfileStationValues {
  alku: number | null;
  taite: number | null;
  loppu: number | null;
}

export interface ExtProfileRemark {
  koodi: string;
  selite: string;
}

export interface ExtProfilePviPoint {
  pyoristyksen_alku: ExtProfileCurvedSectionEndpoint;
  taite: ExtProfileIntersectionPoint;
  pyoristyksen_loppu: ExtProfileCurvedSectionEndpoint;
  pyoristyssade: number;
  tangentti: number;
  kaltevuusjakso_taaksepain: ExtProfileLinearSection;
  kaltevuusjakso_eteenpain: ExtProfileLinearSection;
  paaluluku: ExtProfileStationValues;
  suunnitelman_korkeusjarjestelma?: string;
  suunnitelman_korkeusasema?: string;
  huomiot: ExtProfileRemark[];
}

export interface ExtProfileAddressRange {
  alku: string;
  loppu: string;
  taitepisteet: ExtProfilePviPoint[];
}

export interface ExtLocationTrackProfileResponse {
  rataverkon_versio: string;
  sijaintiraide_oid: string;
  koordinaatisto: string;
  osoitevali: ExtProfileAddressRange;
}

export interface ExtMeasureAddressPoint {
  x: number;
  y: number;
  osoitevali_m: number;
  rataosoite: string;
}

export interface ExtGeometryAddressRange {
  alku: string;
  loppu: string;
  pisteet: ExtMeasureAddressPoint[];
}

export interface ExtLocationTrackGeometryResponse {
  rataverkon_versio: string;
  sijaintiraide_oid: string;
  koordinaatisto: string;
  osoitevali: ExtGeometryAddressRange;
}

export interface ExtLocationTrackResponse {
  rataverkon_versio: string;
  koordinaatisto: string;
  sijaintiraide: ExtLocationTrack;
}

export interface LocationTrackResponse {
  // The track's basic info (fetched for the track's name; the track-number listing has
  // it too, but route sections arrive with only the track's oid).
  info?: ExtLocationTrack;
  profile: ExtLocationTrackProfileResponse;
  geometry: ExtLocationTrackGeometryResponse;
}

export type ExtRouteEndpointType =
  | "sijainti_raiteella"
  | "vaihde"
  | "raiteen_pää";

export type ExtRouteDirection = "nouseva" | "laskeva";

export interface ExtRouteSectionEndpoint {
  tyyppi: ExtRouteEndpointType;
  vaihde_oid?: string;
  rataosoite?: string;
  x: number;
  y: number;
  m_arvo: number;
}

export interface ExtRouteSection {
  sijaintiraide_oid: string;
  ratanumero_oid: string;
  alku: ExtRouteSectionEndpoint;
  loppu: ExtRouteSectionEndpoint;
  suunta: ExtRouteDirection;
  pituus: number;
}

export interface ExtRoute {
  pituus: number;
  reitin_osat: ExtRouteSection[];
}

export interface ExtRouteResponse {
  rataverkon_versio: string;
  koordinaatisto: string;
  reitti: ExtRoute;
}

export type Environment = "local" | "dev" | "test" | "prod";
