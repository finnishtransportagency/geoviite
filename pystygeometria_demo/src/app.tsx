import React from "react";
import { useTranslation } from "react-i18next";
import { useAppDispatch, useAppSelector } from "./store/store";
import { fetchCommonData } from "./store/data-slice";
import { ApiSettings } from "./components/api-settings";
import { TrackNumberSelect } from "./components/track-number-select";
import { AddressRangeInputs } from "./components/address-range-inputs";
import { LocationTrackList } from "./components/location-track-list";
import { DiagramContainer } from "./components/diagram-container";

export const App: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const config = useAppSelector((state) => state.config);

  // Load the track numbers and operational points on startup, and again whenever the
  // API config changes (committing a config change resets all loaded data).
  React.useEffect(() => {
    dispatch(fetchCommonData());
  }, [dispatch, config]);

  return (
    <div className="app">
      <h1>{t("title")}</h1>
      <ApiSettings />
      <div className="selection-row">
        <TrackNumberSelect />
        <AddressRangeInputs />
      </div>
      <div className="main-row">
        <div className="side-panel">
          <LocationTrackList />
        </div>
        <div className="diagram-panel">
          <DiagramContainer />
        </div>
      </div>
    </div>
  );
};
