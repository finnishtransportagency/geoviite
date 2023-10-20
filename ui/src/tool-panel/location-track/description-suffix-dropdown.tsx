import React from 'react';
import { Dropdown, DropdownSize } from 'vayla-design-lib/dropdown/dropdown';
import { descriptionSuffixModes } from 'utils/enum-localization-utils';
import { LocationTrackDescriptionSuffixMode } from 'track-layout/track-layout-model';

type DescriptionSuffixDropdownProps = {
    suffixMode: LocationTrackDescriptionSuffixMode | undefined;
    onChange: (value: LocationTrackDescriptionSuffixMode) => void;
    onBlur: () => void;
    size?: DropdownSize;
    disabled?: boolean;
};

export const DescriptionSuffixDropdown: React.FC<DescriptionSuffixDropdownProps> = ({
    suffixMode,
    onChange,
    onBlur,
    size,
    disabled = false,
}) => {
    return (
        <Dropdown
            options={descriptionSuffixModes}
            value={suffixMode ?? 'NONE'}
            onChange={onChange}
            onBlur={onBlur}
            canUnselect={false}
            wideList
            wide
            size={size}
            disabled={disabled}
        />
    );
};
