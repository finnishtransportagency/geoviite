import React from "react";
import { useTranslation } from "react-i18next";
import Select from "react-select";
import { useAppDispatch, useAppSelector } from "../store/store";
import { fetchRoute, routeRequestKey, RouteRequest } from "../store/data-slice";
import {
  routeEndSet,
  routeEndpointsSwapped,
  routeStartSet,
} from "../store/selection-slice";
import { ExtOperationalPoint } from "../api/types";

// Route-mode selection UI: the user picks the route's start and end operational
// points, and the routing API finds the location tracks between them.

interface OperationalPointOption {
  value: string;
  point: ExtOperationalPoint;
}

const toOption = (point: ExtOperationalPoint): OperationalPointOption => ({
  value: point.toiminnallinen_piste_oid,
  point,
});

const optionLabel = (option: OperationalPointOption) =>
  option.point.lyhenne
    ? `${option.point.nimi} (${option.point.lyhenne})`
    : option.point.nimi;

const optionMatches = (
  option: { data: OperationalPointOption },
  rawInput: string,
) => {
  const input = rawInput.trim().toLowerCase();
  const point = option.data.point;
  return (
    input === "" ||
    point.nimi.toLowerCase().includes(input) ||
    (point.lyhenne ?? "").toLowerCase().includes(input) ||
    point.toiminnallinen_piste_oid.includes(input)
  );
};

const OperationalPointSelect: React.FC<{
  options: OperationalPointOption[];
  selectedOid?: string;
  placeholder: string;
  isLoading: boolean;
  onChange: (oid: string | undefined) => void;
}> = ({ options, selectedOid, placeholder, isLoading, onChange }) => (
  <Select<OperationalPointOption>
    isClearable={true}
    options={options}
    value={options.find((option) => option.value === selectedOid) ?? null}
    getOptionLabel={optionLabel}
    filterOption={optionMatches}
    isLoading={isLoading}
    placeholder={placeholder}
    onChange={(option) => {
      onChange(option?.value);
    }}
  />
);

export const RouteSelect: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const commonData = useAppSelector((state) => state.data.commonData);
  const route = useAppSelector((state) => state.data.route);
  const startOid = useAppSelector((state) => state.selection.routeStartOid);
  const endOid = useAppSelector((state) => state.selection.routeEndOid);

  // Only operational points with a map location can bound a route: the routing API is
  // called with the points' coordinates.
  const points = React.useMemo(
    () =>
      (commonData.data?.operationalPoints ?? []).filter(
        (point) => point.sijainti,
      ),
    [commonData.data],
  );
  const options = React.useMemo(() => points.map(toOption), [points]);

  const startPoint = points.find(
    (point) => point.toiminnallinen_piste_oid === startOid,
  );
  const endPoint = points.find(
    (point) => point.toiminnallinen_piste_oid === endOid,
  );

  // Fetch the route whenever both endpoints are chosen and the current route state is
  // for some other request (or for none, after a data reset).
  React.useEffect(() => {
    if (!startPoint?.sijainti || !endPoint?.sijainti) {
      return;
    }
    const request: RouteRequest = {
      start: startPoint.sijainti,
      end: endPoint.sijainti,
    };
    if (route.status === "idle" || route.key !== routeRequestKey(request)) {
      dispatch(fetchRoute(request));
    }
  }, [dispatch, startPoint, endPoint, route.status, route.key]);

  const summary = route.status === "ready" && route.data && (
    <div className="hint-text">
      {t("routeSummary", {
        lengthKm: (route.data.reitti.pituus / 1000).toFixed(1),
        sections: route.data.reitti.reitin_osat.length,
        tracks: new Set(
          route.data.reitti.reitin_osat.map(
            (section) => section.sijaintiraide_oid,
          ),
        ).size,
      })}
    </div>
  );

  return (
    <div className="route-select">
      <OperationalPointSelect
        options={options}
        selectedOid={startOid}
        placeholder={t("routeStartPoint")}
        isLoading={commonData.status === "loading"}
        onChange={(oid) => dispatch(routeStartSet(oid))}
      />
      <OperationalPointSelect
        options={options}
        selectedOid={endOid}
        placeholder={t("routeEndPoint")}
        isLoading={commonData.status === "loading"}
        onChange={(oid) => dispatch(routeEndSet(oid))}
      />
      <div className="route-select__status">
        <button
          type="button"
          disabled={!startOid && !endOid}
          onClick={() => dispatch(routeEndpointsSwapped())}
        >
          {t("swapRouteEndpoints")}
        </button>
        {route.status === "loading" && (
          <span className="hint-text">{t("loadingRoute")}</span>
        )}
        {route.status === "error" && (
          <span className="error-text">
            {t("failedToLoadRoute", { error: route.error })}
          </span>
        )}
        {route.status === "ready" && route.data === null && (
          <span className="hint-text">{t("noRouteFound")}</span>
        )}
      </div>
      {summary}
    </div>
  );
};
