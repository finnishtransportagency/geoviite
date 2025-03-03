import * as React from 'react';
import { Dropdown, dropdownOption, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
    OperatingPoint,
} from 'track-layout/track-layout-model';
import { getLocationTrackDescriptions } from 'track-layout/layout-location-track-api';
import { getBySearchTerm } from 'track-layout/track-layout-search-api';
import { isNilOrBlank } from 'utils/string-utils';
import { ALIGNMENT_DESCRIPTION_REGEX } from 'tool-panel/location-track/dialog/location-track-validation';
import { LayoutContext } from 'common/common-model';
import { debounceAsync } from 'utils/async-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';

export type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: 'locationTrackSearchItem';
};

function createLocationTrackOptionItem(
    locationTrack: LayoutLocationTrack,
    description: string,
): Item<LocationTrackItemValue> {
    return dropdownOption(
        {
            type: 'locationTrackSearchItem',
            locationTrack: locationTrack,
        } as const,
        `${locationTrack.name}, ${description}`,
        `location-track-${locationTrack.id}`,
    );
}

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: 'switchSearchItem';
};

function createSwitchOptionItem(layoutSwitch: LayoutSwitch): Item<SwitchItemValue> {
    return dropdownOption(
        {
            type: 'switchSearchItem',
            layoutSwitch: layoutSwitch,
        } as const,
        layoutSwitch.name,
        `switch-${layoutSwitch.id}`,
    );
}

export type TrackNumberItemValue = {
    trackNumber: LayoutTrackNumber;
    type: 'trackNumberSearchItem';
};

function createTrackNumberOptionItem(
    layoutTrackNumber: LayoutTrackNumber,
): Item<TrackNumberItemValue> {
    return dropdownOption(
        {
            type: 'trackNumberSearchItem',
            trackNumber: layoutTrackNumber,
        } as const,
        layoutTrackNumber.number,
        `track-number-${layoutTrackNumber.id}`,
    );
}

type OperatingPointItemValue = {
    operatingPoint: OperatingPoint;
    type: 'operatingPointSearchItem';
};

function createOperatingPointOptionItem(
    operatingPoint: OperatingPoint,
): Item<OperatingPointItemValue> {
    return dropdownOption(
        {
            operatingPoint: operatingPoint,
            type: 'operatingPointSearchItem',
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
    searchTypes: SearchType[],
): Promise<Item<SearchItemValue>[]> {
    if (isNilOrBlank(searchTerm) || !searchTerm.match(SEARCH_REGEX)) {
        return Promise.resolve([]);
    }

    const searchResult = await getBySearchTerm(
        searchTerm,
        layoutContext,
        searchTypes,
        locationTrackSearchScope,
    );

    const locationTrackDescriptions = await getLocationTrackDescriptions(
        searchResult.locationTracks.map((lt) => lt.id),
        layoutContext,
    );

    const locationTrackOptions = searchResult.locationTracks.map((locationTrack) => {
        const description =
            locationTrackDescriptions?.find((d) => d.id === locationTrack.id)?.description ?? '';

        return createLocationTrackOptionItem(locationTrack, description);
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

export type SearchItemValue =
    | LocationTrackItemValue
    | SwitchItemValue
    | TrackNumberItemValue
    | OperatingPointItemValue;

type SearchDropdownProps = {
    layoutContext: LayoutContext;
    splittingState?: SplittingState;
    placeholder: string;
    disabled?: boolean;
    onItemSelected: (item: SearchItemValue | undefined) => void;
    searchTypes: SearchType[];
    onBlur?: () => void;
    hasError?: boolean;
    value?: SearchItemValue;
    getName?: (item: SearchItemValue) => string;
};

export enum SearchType {
    LOCATION_TRACK = 'LOCATION_TRACK',
    SWITCH = 'SWITCH',
    TRACK_NUMBER = 'TRACK_NUMBER',
    OPERATING_POINT = 'OPERATING_POINT',
}

export const SearchDropdown: React.FC<SearchDropdownProps> = ({
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
}) => {
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
            size={DropdownSize.STRETCH}
            onBlur={onBlur}
            hasError={hasError}
            value={value}
            getName={getName}
            wideList
            wide
            qa-id="search-box"
        />
    );
};
