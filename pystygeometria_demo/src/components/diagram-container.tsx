import React from "react";
import { useTranslation } from "react-i18next";
import { useAppDispatch, useAppSelector } from "../store/store";
import { viewRangeSet } from "../store/view-slice";
import {
  buildDiagramTracks,
  buildRouteDiagramTracks,
} from "../math/diagram-model";
import { computeLayout, Layout, remapViewKeepingCenter } from "../math/layout";
import { compareTrackAddresses } from "../math/track-address";
import { locationTrackAddressRange } from "../math/selection";
import { routeSpans } from "../math/route";
import { Diagram } from "./diagram";
import { defaultDiagramDimensions } from "../math/coordinates";
import { placeOperationalPoints } from "../math/operational-points";

export const DiagramContainer: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const mode = useAppSelector((state) => state.selection.mode);
  const trackNumberTracks = useAppSelector(
    (state) => state.data.trackNumberTracks.data,
  );
  const route = useAppSelector((state) => state.data.route);
  const locationTracks = useAppSelector((state) => state.data.locationTracks);
  const operationalPoints = useAppSelector(
    (state) => state.data.commonData.data?.operationalPoints,
  );
  const selectedOids = useAppSelector(
    (state) => state.selection.selectedLocationTrackOids,
  );
  const viewRange = useAppSelector((state) => state.view.range);

  // The track oids the current selection displays: the route's sections in route
  // order, or the hand-picked location tracks sorted by start address.
  const spans = React.useMemo(
    () => (mode === "route" && route.data ? routeSpans(route.data.reitti) : []),
    [mode, route.data],
  );

  const displayedOids = React.useMemo(
    () =>
      mode === "route"
        ? [...new Set(spans.map((span) => span.trackOid))]
        : selectedOids,
    [mode, spans, selectedOids],
  );

  const tracks = React.useMemo(() => {
    const responseByOid = Object.fromEntries(
      Object.entries(locationTracks).map(([oid, entry]) => [oid, entry.data]),
    );
    if (mode === "route") {
      return buildRouteDiagramTracks(spans, responseByOid);
    }
    const selected = (trackNumberTracks ?? [])
      .filter((track) => selectedOids.includes(track.sijaintiraide_oid))
      .sort((a, b) => {
        const aRange = locationTrackAddressRange(a);
        const bRange = locationTrackAddressRange(b);
        return aRange && bRange
          ? compareTrackAddresses(aRange.start, bRange.start)
          : 0;
      });
    return buildDiagramTracks(selected, responseByOid);
  }, [mode, spans, trackNumberTracks, locationTracks, selectedOids]);

  const operationalPointPlacements = React.useMemo(
    () => placeOperationalPoints(tracks, operationalPoints ?? []),
    [tracks, operationalPoints],
  );

  const layout = React.useMemo(() => computeLayout(tracks), [tracks]);

  // When the displayed track spans change (selection toggled, or a profile finished
  // loading), remap a user-set view so its center keeps pointing at the same m-value
  // on the same track where possible.
  const layoutKey = layout.spans
    .map((span) => `${span.key}:${span.lengthM}`)
    .join("|");
  const previousLayout = React.useRef<{ layout: Layout; key: string }>();
  React.useEffect(() => {
    const previous = previousLayout.current;
    if (previous && previous.key !== layoutKey && viewRange) {
      dispatch(
        viewRangeSet(
          remapViewKeepingCenter(viewRange, previous.layout, layout),
        ),
      );
    }
    previousLayout.current = { layout, key: layoutKey };
  }, [layoutKey, layout, viewRange, dispatch]);

  const loadingCount = displayedOids.filter(
    (oid) => locationTracks[oid]?.status === "loading",
  ).length;
  const profileErrors = displayedOids.flatMap((oid) => {
    const entry = locationTracks[oid];
    return entry?.status === "error" ? [`${oid}: ${entry.error}`] : [];
  });

  if (layout.totalLength === 0) {
    const placeholder =
      mode === "route"
        ? route.status === "loading"
          ? t("loadingRoute")
          : route.status === "error"
            ? t("failedToLoadRoute", { error: route.error })
            : route.status === "ready" && route.data === null
              ? t("noRouteFound")
              : route.data
                ? loadingCount > 0
                  ? t("loadingVerticalGeometry")
                  : t("noVerticalGeometry")
                : t("selectRoutePoints")
        : selectedOids.length === 0
          ? t("selectLocationTracks")
          : loadingCount > 0
            ? t("loadingVerticalGeometry")
            : t("noVerticalGeometry");
    return (
      <div
        className="diagram-placeholder"
        style={{
          width: defaultDiagramDimensions.widthPx,
          height: defaultDiagramDimensions.heightPx,
        }}
      >
        {placeholder}
        {profileErrors.map((error) => (
          <div key={error} className="error-text">
            {error}
          </div>
        ))}
      </div>
    );
  }

  const view = viewRange ?? { startX: 0, endX: layout.totalLength };

  return (
    <div>
      <Diagram
        tracks={tracks}
        layout={layout}
        view={view}
        operationalPoints={operationalPointPlacements}
        onViewChange={(newView) => dispatch(viewRangeSet(newView))}
      />
      <div className="diagram-status">
        {loadingCount > 0 && (
          <span>{t("loadingProfiles", { count: loadingCount })} </span>
        )}
        {profileErrors.map((error) => (
          <span key={error} className="error-text">
            {error}{" "}
          </span>
        ))}
      </div>
    </div>
  );
};
