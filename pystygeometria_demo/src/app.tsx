import React from "react";
import { useTranslation } from "react-i18next";
import { useAppDispatch, useAppSelector } from "./store/store";
import { fetchCommonData } from "./store/data-slice";
import { SelectionMode, selectionModeSet } from "./store/selection-slice";
import { ApiSettings } from "./components/api-settings";
import { TrackNumberSelect } from "./components/track-number-select";
import { AddressRangeInputs } from "./components/address-range-inputs";
import { LocationTrackList } from "./components/location-track-list";
import { RouteSelect } from "./components/route-select";
import { DiagramContainer } from "./components/diagram-container";

// Chooses between the two ways of picking the displayed track spans: by track number
// and address range, or by routing between two operational points.
const SelectionModeToggle: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const mode = useAppSelector((state) => state.selection.mode);
  const modes: SelectionMode[] = ["trackNumber", "route"];
  return (
    <div className="selection-mode-toggle">
      {modes.map((option) => (
        <label key={option}>
          <input
            type="radio"
            name="selection-mode"
            checked={mode === option}
            onChange={() => dispatch(selectionModeSet(option))}
          />{" "}
          {t(
            option === "trackNumber"
              ? "selectionModeTrackNumber"
              : "selectionModeRoute",
          )}
        </label>
      ))}
    </div>
  );
};

export const App: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const config = useAppSelector((state) => state.config);
  const selectionMode = useAppSelector((state) => state.selection.mode);

  // Load the track numbers and operational points on startup, and again whenever the
  // API config changes (committing a config change resets all loaded data).
  React.useEffect(() => {
    dispatch(fetchCommonData());
  }, [dispatch, config]);

  return (
    <div className="app">
      <h1>{t("title")}</h1>
      <section className="box">
        <h2 className="box__heading">{t("boxApiEnvironment")}</h2>
        <ApiSettings />
      </section>
      <div className="main-row">
        <section className="box diagram-box">
          <h2 className="box__heading">{t("boxDiagram")}</h2>
          <DiagramContainer />
        </section>
        <section className="box selection-box">
          <h2 className="box__heading">{t("boxTrackSelection")}</h2>
          <SelectionModeToggle />
          {selectionMode === "trackNumber" ? (
            <>
              <TrackNumberSelect />
              <div className="selection-row">
                <AddressRangeInputs />
              </div>
              <LocationTrackList />
            </>
          ) : (
            <RouteSelect />
          )}
        </section>
      </div>
    </div>
  );
};
