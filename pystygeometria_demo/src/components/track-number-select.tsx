import React from "react";
import { useTranslation } from "react-i18next";
import Select from "react-select";
import { useAppDispatch, useAppSelector } from "../store/store";
import { fetchTrackNumberTracks } from "../store/data-slice";
import { trackNumberSelected } from "../store/selection-slice";
import { ExtTrackNumber } from "../api/types";

interface TrackNumberOption {
  value: string;
  trackNumber: ExtTrackNumber;
}

const toOption = (trackNumber: ExtTrackNumber): TrackNumberOption => ({
  value: trackNumber.ratanumero_oid,
  trackNumber,
});

const optionLabel = (option: TrackNumberOption) =>
  `${option.trackNumber.ratanumero} — ${option.trackNumber.kuvaus} (${option.trackNumber.ratanumero_oid})`;

const optionMatches = (
  option: { data: TrackNumberOption },
  rawInput: string,
) => {
  const input = rawInput.trim().toLowerCase();
  const tn = option.data.trackNumber;
  return (
    input === "" ||
    tn.ratanumero_oid.toLowerCase().includes(input) ||
    tn.ratanumero.toLowerCase().includes(input) ||
    tn.kuvaus.toLowerCase().includes(input)
  );
};

export const TrackNumberSelect: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const commonData = useAppSelector((state) => state.data.commonData);
  const selectedOid = useAppSelector((state) => state.selection.trackNumberOid);

  const options = React.useMemo(
    () => (commonData?.data?.trackNumbers ?? []).map(toOption),
    [commonData?.data],
  );
  const selectedOption =
    options.find((option) => option.value === selectedOid) ?? null;

  return (
    <div className="track-number-select">
      <Select<TrackNumberOption>
        options={options}
        value={selectedOption}
        getOptionLabel={optionLabel}
        filterOption={optionMatches}
        isLoading={commonData.status === "loading"}
        placeholder={t("searchTrackNumber")}
        onChange={(option) => {
          if (!option) {
            return;
          }
          const tn = option.trackNumber;
          dispatch(
            trackNumberSelected({
              oid: tn.ratanumero_oid,
              addressStart: tn.alkusijainti?.rataosoite ?? "",
              addressEnd: tn.loppusijainti?.rataosoite ?? "",
            }),
          );
          dispatch(fetchTrackNumberTracks(tn.ratanumero_oid));
        }}
      />
      {commonData.status === "error" && (
        <div className="error-text">
          {t("failedToLoadTrackNumbers", { error: commonData.error })}
        </div>
      )}
    </div>
  );
};
