import React from "react";
import { useTranslation } from "react-i18next";
import { useAppDispatch, useAppSelector } from "../store/store";
import { addressEndSet, addressStartSet } from "../store/selection-slice";
import { parseTrackAddress } from "../math/track-address";

const AddressInput: React.FC<{
  label: string;
  value: string;
  onChange: (value: string) => void;
}> = ({ label, value, onChange }) => {
  const valid = value === "" || parseTrackAddress(value) !== undefined;
  return (
    <label>
      {label}{" "}
      <input
        type="text"
        size={13}
        className={valid ? "" : "input-invalid"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="0000+0000.000"
      />
    </label>
  );
};

export const AddressRangeInputs: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const addressStart = useAppSelector((state) => state.selection.addressStart);
  const addressEnd = useAppSelector((state) => state.selection.addressEnd);

  return (
    <div className="address-range-inputs">
      <AddressInput
        label={t("addressRangeStart")}
        value={addressStart}
        onChange={(value) => dispatch(addressStartSet(value))}
      />
      <AddressInput
        label={t("addressRangeEnd")}
        value={addressEnd}
        onChange={(value) => dispatch(addressEndSet(value))}
      />
    </div>
  );
};
