import * as React from 'react';
import { Dropdown, dropdownOption, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
    OperatingPoint,
} from 'track-layout/track-layout-model';
import { getBySearchTerm } from 'track-layout/track-layout-search-api';
import { isNilOrBlank } from 'utils/string-utils';
import { ALIGNMENT_DESCRIPTION_REGEX } from 'tool-panel/location-track/dialog/location-track-validation';
import { LayoutContext } from 'common/common-model';
import { debounceAsync } from 'utils/async-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';

export type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: SearchItemType.LOCATION_TRACK;
};

function createLocationTrackOptionItem(
    locationTrack: LayoutLocationTrack,
): Item<LocationTrackItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.LOCATION_TRACK,
            locationTrack: locationTrack,
        } as const,
        `${locationTrack.name}, ${locationTrack.description}`,
        `location-track-${locationTrack.id}`,
    );
}

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: SearchItemType.SWITCH;
};

function createSwitchOptionItem(layoutSwitch: LayoutSwitch): Item<SwitchItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.SWITCH,
            layoutSwitch: layoutSwitch,
        } as const,
        layoutSwitch.name,
        `switch-${layoutSwitch.id}`,
    );
}

export type TrackNumberItemValue = {
    trackNumber: LayoutTrackNumber;
    type: SearchItemType.TRACK_NUMBER;
};

function createTrackNumberOptionItem(
    layoutTrackNumber: LayoutTrackNumber,
): Item<TrackNumberItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.TRACK_NUMBER,
            trackNumber: layoutTrackNumber,
        } as const,
        layoutTrackNumber.number,
        `track-number-${layoutTrackNumber.id}`,
    );
}

type OperatingPointItemValue = {
    operatingPoint: OperatingPoint;
    type: SearchItemType.OPERATING_POINT;
};

function createOperatingPointOptionItem(
    operatingPoint: OperatingPoint,
): Item<OperatingPointItemValue> {
    return dropdownOption(
        {
            operatingPoint: operatingPoint,
            type: SearchItemType.OPERATING_POINT,
        } as const,
        `${operatingPoint.name}, ${operatingPoint.abbreviation}`,
        `operating-point-${operatingPoint.name}`,
    );
}

// The characters that alignment descriptions can contain is a superset of the characters that can be used in search,
// and it's considered quite likely to stay that way even if allowed character sets for names etc. are changed.
const SEARCH_REGEX = ALIGNMENT_DESCRIPTION_REGEX;

async function getOptions(
    layoutContext: LayoutContext,
    searchTerm: string,
    locationTrackSearchScope: LocationTrackId | undefined,
    searchTypes: SearchItemType[],
): Promise<Item<SearchItemValue<SearchItemType>>[]> {
    if (isNilOrBlank(searchTerm) || !searchTerm.match(SEARCH_REGEX)) {
        return Promise.resolve([]);
    }

    const searchResult = await getBySearchTerm(
        searchTerm,
        layoutContext,
        searchTypes,
        locationTrackSearchScope,
    );

    const locationTrackOptions = searchResult.locationTracks.map((locationTrack) => {
        return createLocationTrackOptionItem(locationTrack);
    });

    return [
        searchResult.operatingPoints.map(createOperatingPointOptionItem),
        locationTrackOptions,
        searchResult.switches.map(createSwitchOptionItem),
        searchResult.trackNumbers.map(createTrackNumberOptionItem),
    ].flat();
}

// Use debounced function to collect keystrokes before triggering a search
const debouncedGetOptions = debounceAsync(getOptions, 250);

export type SearchItemValue<SearchTypes extends SearchItemType> =
    SearchTypes extends 'LOCATION_TRACK'
        ? LocationTrackItemValue
        : SearchTypes extends 'SWITCH'
          ? SwitchItemValue
          : SearchTypes extends 'TRACK_NUMBER'
            ? TrackNumberItemValue
            : SearchTypes extends 'OPERATING_POINT'
              ? OperatingPointItemValue
              : never;

type SearchDropdownProps<SearchTypes extends SearchItemType> = {
    layoutContext: LayoutContext;
    splittingState?: SplittingState;
    placeholder: string;
    disabled?: boolean;
    onItemSelected: (item: SearchItemValue<SearchTypes> | undefined) => void;
    searchTypes: SearchTypes[];
    onBlur?: () => void;
    hasError?: boolean;
    value?: SearchItemValue<SearchTypes>;
    getName?: (item: SearchItemValue<SearchTypes>) => string;
    size?: DropdownSize;
    wide?: boolean;
    useAnchorElementWidth?: boolean;
    clearable?: boolean;
};

export enum SearchItemType {
    LOCATION_TRACK = 'LOCATION_TRACK',
    SWITCH = 'SWITCH',
    TRACK_NUMBER = 'TRACK_NUMBER',
    OPERATING_POINT = 'OPERATING_POINT',
}

export const SearchDropdown = <SearchTypes extends SearchItemType>({
    layoutContext,
    placeholder,
    disabled = false,
    onItemSelected,
    splittingState,
    searchTypes,
    onBlur,
    hasError,
    value,
    getName,
    size = DropdownSize.STRETCH,
    wide = true,
    useAnchorElementWidth,
    clearable,
}: SearchDropdownProps<SearchTypes>) => {
    // Use memoized function to make debouncing functionality to work when re-rendering
    const memoizedDebouncedGetOptions = React.useCallback(
        (searchTerm: string) =>
            debouncedGetOptions(
                layoutContext,
                searchTerm,
                splittingState?.originLocationTrack?.id,
                searchTypes,
            ),
        [layoutContext, splittingState],
    );

    return (
        <Dropdown
            placeholder={placeholder}
            disabled={disabled}
            options={memoizedDebouncedGetOptions}
            searchable
            onChange={onItemSelected}
            size={size}
            onBlur={onBlur}
            hasError={hasError}
            value={value}
            getName={getName}
            wideList
            wide={wide}
            useAnchorElementWidth={useAnchorElementWidth}
            qa-id="search-box"
            clearable={clearable}
        />
    );
};
