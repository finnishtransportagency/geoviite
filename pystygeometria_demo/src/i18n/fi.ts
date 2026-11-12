const fi = {
  translation: {
    title: "Pystygeometriakaavio",

    boxApiEnvironment: "API-ympäristö",
    boxDiagram: "Kaavio",
    boxTrackSelection: "Raiteiden valinta",

    environment: "Ympäristö",
    devApiKey: "Dev API-avain",
    prodApiKey: "Prod API-avain",

    addressRangeStart: "Rataosoitevälin alku",
    addressRangeEnd: "loppu",

    selectLocationTracks:
      "Valitse sijaintiraiteet pystygeometrian näyttämiseksi.",
    loadingVerticalGeometry: "Ladataan pystygeometriaa…",
    noVerticalGeometry:
      "Valituilla raiteilla ei ole näytettävää pystygeometriaa.",
    loadingProfiles: "Ladataan {{count}} profiilia…",

    selectTrackNumber: "Valitse ratanumero näyttääksesi sen sijaintiraiteet.",
    loadingLocationTracks: "Ladataan sijaintiraiteita…",
    failedToLoadLocationTracks:
      "Sijaintiraiteiden lataus epäonnistui: {{error}}",
    trackType_pääraide: "Pääraide",
    trackType_sivuraide: "Sivuraide",
    trackType_kujaraide: "Kujaraide",
    trackType_turvaraide: "Turvaraide",
    trackTypeFilter: "Raidetyyppi",
    locationTracksInRange:
      "{{listed}}/{{total}} sijaintiraidetta osoitevälillä, {{selected}} valittu",
    searchTrackNumber: "Hae ratanumeroa OID:lla tai kuvauksella…",
    failedToLoadTrackNumbers: "Ratanumeroiden lataus epäonnistui: {{error}}",

    selectionModeTrackNumber: "Ratanumero ja osoiteväli",
    selectionModeRoute: "Reitti toiminnallisten pisteiden välillä",
    routeStartPoint: "Valitse reitin alkupiste…",
    routeEndPoint: "Valitse reitin loppupiste…",
    swapRouteEndpoints: "⇅ Vaihda suunta",
    selectRoutePoints:
      "Valitse alku- ja loppupiste näyttääksesi reitin pystygeometrian.",
    loadingRoute: "Haetaan reittiä…",
    noRouteFound: "Valittujen pisteiden välille ei löytynyt reittiä.",
    failedToLoadRoute: "Reitin haku epäonnistui: {{error}}",
    routeSummary:
      "Reitin pituus {{lengthKm}} km, {{sections}} osaa {{tracks}} sijaintiraiteella",
  },
};

export default fi;
