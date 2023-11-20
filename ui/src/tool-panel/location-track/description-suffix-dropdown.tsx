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
    qaId?: string;
};

export const DescriptionSuffixDropdown: React.FC<DescriptionSuffixDropdownProps> = ({
    suffixMode,
    onChange,
    onBlur,
    size,
    qaId,
    disabled = false,
}) => {
    const options = descriptionSuffixModes.map((s) => ({ ...s, qaId: s.value }));

    return (
        <Dropdown
            qaId={qaId}
            options={options}
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
