import * as React from 'react';
import { Dropdown, dropdownOption, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import {
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
    OperationalPoint,
} from 'track-layout/track-layout-model';
import { getBySearchTerm } from 'track-layout/track-layout-search-api';
import { isNilOrBlank } from 'utils/string-utils';
import { ALIGNMENT_DESCRIPTION_REGEX } from 'tool-panel/location-track/dialog/location-track-validation';
import { LayoutContext } from 'common/common-model';
import { debounceAsync } from 'utils/async-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { useTrackNumbersIncludingDeleted } from 'track-layout/track-layout-react-utils';
import { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import {
    kmPostSearchItemName,
    locationTrackSearchItemName,
    operationalPointItemName,
    SearchDropdownItem,
} from 'asset-search/search-dropdown-item';

export type LocationTrackItemValue = {
    locationTrack: LayoutLocationTrack;
    type: SearchItemType.LOCATION_TRACK;
};

function createLocationTrackOptionItem(
    locationTrack: LayoutLocationTrack,
    t: TFunction<'translation', undefined>,
): Item<LocationTrackItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.LOCATION_TRACK,
            locationTrack: locationTrack,
        } as const,
        locationTrackSearchItemName(locationTrack, t),
        `location-track-${locationTrack.id}`,
        <SearchDropdownItem
            name={locationTrackSearchItemName(locationTrack, t)}
            isDeleted={locationTrack.state === 'DELETED'}
            deletedPhrase={t('enum.LocationTrackState.DELETED')}
        />,
    );
}

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: SearchItemType.SWITCH;
};

function createSwitchOptionItem(
    layoutSwitch: LayoutSwitch,
    t: TFunction<'translation', undefined>,
): Item<SwitchItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.SWITCH,
            layoutSwitch: layoutSwitch,
        } as const,
        layoutSwitch.name,
        `switch-${layoutSwitch.id}`,
        <SearchDropdownItem
            name={layoutSwitch.name}
            isDeleted={layoutSwitch.stateCategory === 'NOT_EXISTING'}
            deletedPhrase={t('enum.LayoutStateCategory.NOT_EXISTING')}
        />,
    );
}

export type TrackNumberItemValue = {
    trackNumber: LayoutTrackNumber;
    type: SearchItemType.TRACK_NUMBER;
};

export type KmPostItemValue = {
    kmPost: LayoutKmPost;
    type: SearchItemType.KM_POST;
};

function createTrackNumberOptionItem(
    layoutTrackNumber: LayoutTrackNumber,
    t: TFunction<'translation', undefined>,
): Item<TrackNumberItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.TRACK_NUMBER,
            trackNumber: layoutTrackNumber,
        } as const,
        layoutTrackNumber.number,
        `track-number-${layoutTrackNumber.id}`,
        <SearchDropdownItem
            name={layoutTrackNumber.number}
            isDeleted={layoutTrackNumber.state === 'DELETED'}
            deletedPhrase={t('enum.LayoutState.DELETED')}
        />,
    );
}

function createKmPostOptionItem(
    layoutKmPost: LayoutKmPost,
    allTrackNumbers: LayoutTrackNumber[],
    t: TFunction<'translation', undefined>,
): Item<KmPostItemValue> {
    return dropdownOption(
        {
            type: SearchItemType.KM_POST,
            kmPost: layoutKmPost,
        } as const,
        kmPostSearchItemName(layoutKmPost, allTrackNumbers, t),
        `km-post-${layoutKmPost.id}`,
        <SearchDropdownItem
            name={kmPostSearchItemName(layoutKmPost, allTrackNumbers, t)}
            isDeleted={layoutKmPost.state === 'DELETED'}
            deletedPhrase={t('enum.LayoutState.DELETED')}
        />,
    );
}

type OperationalPointItemValue = {
    operationalPoint: OperationalPoint;
    type: SearchItemType.OPERATIONAL_POINT;
};

function createOperationalPointOptionItem(
    operationalPoint: OperationalPoint,
    t: TFunction<'translation', undefined>,
): Item<OperationalPointItemValue> {
    return dropdownOption(
        {
            operationalPoint: operationalPoint,
            type: SearchItemType.OPERATIONAL_POINT,
        } as const,
        operationalPoint.abbreviation === undefined
            ? operationalPoint.name
            : `${operationalPoint.name}, ${operationalPoint.abbreviation}`,
        `operational-point-${operationalPoint.name}`,
        <SearchDropdownItem
            name={operationalPointItemName(operationalPoint, t)}
            isDeleted={operationalPoint.state === 'DELETED'}
            deletedPhrase={t('enum.OperationalPointState.DELETED')}
        />,
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
    allTrackNumbers: LayoutTrackNumber[],
    t: TFunction<'translation', undefined>,
    includeDeleted: boolean,
): Promise<Item<SearchItemValue<SearchItemType>>[]> {
    if (isNilOrBlank(searchTerm) || !searchTerm.match(SEARCH_REGEX)) {
        return Promise.resolve([]);
    }

    const searchResult = await getBySearchTerm(
        searchTerm,
        layoutContext,
        searchTypes,
        includeDeleted,
        locationTrackSearchScope,
    );

    const locationTrackOptions = searchResult.locationTracks.map((locationTrack) => {
        return createLocationTrackOptionItem(locationTrack, t);
    });

    return [
        searchResult.operationalPoints.map((op) => createOperationalPointOptionItem(op, t)),
        locationTrackOptions,
        searchResult.switches.map((sw) => createSwitchOptionItem(sw, t)),
        searchResult.trackNumbers.map((tn) => createTrackNumberOptionItem(tn, t)),
        searchResult.kmPosts.map((kp) => createKmPostOptionItem(kp, allTrackNumbers, t)),
    ].flat();
}

// Use debounced function to collect keystrokes before triggering a search
const debouncedGetOptions = debounceAsync(getOptions, 250);

type SearchItemMap = {
    [SearchItemType.LOCATION_TRACK]: LocationTrackItemValue;
    [SearchItemType.SWITCH]: SwitchItemValue;
    [SearchItemType.TRACK_NUMBER]: TrackNumberItemValue;
    [SearchItemType.OPERATIONAL_POINT]: OperationalPointItemValue;
    [SearchItemType.KM_POST]: KmPostItemValue;
};

export type SearchItemValue<T extends SearchItemType> = SearchItemMap[T];

type SearchDropdownProps<SearchTypes extends SearchItemType> = {
    layoutContext: LayoutContext;
    splittingState?: SplittingState;
    placeholder: string;
    disabled?: boolean;
    onItemSelected: (item: SearchItemValue<SearchTypes> | undefined) => void;
    searchTypes: SearchTypes[];
    includeDeletedAssets: boolean;
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
    OPERATIONAL_POINT = 'OPERATIONAL_POINT',
    KM_POST = 'KM_POST',
}

export const SearchDropdown = <SearchTypes extends SearchItemType>({
    layoutContext,
    placeholder,
    disabled = false,
    onItemSelected,
    splittingState,
    searchTypes,
    includeDeletedAssets,
    onBlur,
    hasError,
    value,
    getName,
    size = DropdownSize.STRETCH,
    wide = true,
    useAnchorElementWidth,
    clearable,
}: SearchDropdownProps<SearchTypes>) => {
    const { t } = useTranslation();
    const trackNumbers = useTrackNumbersIncludingDeleted(layoutContext);

    // Use memoized function to make debouncing functionality to work when re-rendering
    const memoizedDebouncedGetOptions = React.useCallback(
        (searchTerm: string) =>
            debouncedGetOptions(
                layoutContext,
                searchTerm,
                splittingState?.originLocationTrack?.id,
                searchTypes,
                trackNumbers ?? [],
                t,
                includeDeletedAssets,
            ),
        [layoutContext, splittingState, trackNumbers],
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
