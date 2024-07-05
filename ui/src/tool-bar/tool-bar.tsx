import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dropdown, DropdownSize, Item, dropdownOption } from 'vayla-design-lib/dropdown/dropdown';
import { getLocationTrackDescriptions } from 'track-layout/layout-location-track-api';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
    OperatingPoint,
} from 'track-layout/track-layout-model';
import { debounceAsync } from 'utils/async-utils';
import { isNilOrBlank } from 'utils/string-utils';
import {
    BoundingBox,
    boundingBoxAroundPoints,
    centerForBoundingBox,
    expandBoundingBox,
} from 'model/geometry';
import { useTranslation } from 'react-i18next';
import styles from './tool-bar.scss';
import { LocationTrackEditDialogContainer } from 'tool-panel/location-track/dialog/location-track-edit-dialog';
import { SwitchEditDialogContainer } from 'tool-panel/switch/dialog/switch-edit-dialog';
import { KmPostEditDialogContainer } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { TrackNumberEditDialogContainer } from 'tool-panel/track-number/dialog/track-number-edit-dialog';
import { Menu, MenuOption, menuOption } from 'vayla-design-lib/menu/menu';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { MapLayerMenuChange, MapLayerMenuGroups, MapLayerName } from 'map/map-model';
import { getTrackNumberReferenceLine } from 'track-layout/layout-reference-line-api';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import {
    refereshKmPostSelection,
    refreshLocationTrackSelection,
    refreshSwitchSelection,
    refreshTrackNumberSelection,
} from 'track-layout/track-layout-react-utils';
import { getBySearchTerm } from 'track-layout/track-layout-search-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT, VIEW_LAYOUT_DRAFT } from 'user/user-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { TabHeader } from 'geoviite-design-lib/tab-header/tab-header';
import { createClassName } from 'vayla-design-lib/utils';
import { EnvRestricted } from 'environment/env-restricted';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_OPERATING_POINT_BBOX_OFFSET,
} from 'map/map-utils';
import { WorkspaceSelectionContainer } from 'tool-bar/workspace-selection';

export type ToolbarParams = {
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onLayoutContextChange: (context: LayoutContext) => void;
    onOpenPreview: () => void;
    showArea: (area: BoundingBox) => void;
    layoutContext: LayoutContext;
    onStopLinking: () => void;
    onMapLayerChange: (change: MapLayerMenuChange) => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayers: MapLayerName[];
    splittingState: SplittingState | undefined;
    linkingState: LinkingState | undefined;
    selectingWorkspace: boolean;
    setSelectingWorkspace: (selecting: boolean) => void;
};

type LocationTrackItemValue = {
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

type TrackNumberItemValue = {
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

type SearchItemValue =
    | LocationTrackItemValue
    | SwitchItemValue
    | TrackNumberItemValue
    | OperatingPointItemValue;

async function getOptions(
    layoutContext: LayoutContext,
    searchTerm: string,
    locationTrackSearchScope: LocationTrackId | undefined,
): Promise<Item<SearchItemValue>[]> {
    if (isNilOrBlank(searchTerm)) {
        return Promise.resolve([]);
    }

    const searchResult = await getBySearchTerm(searchTerm, layoutContext, locationTrackSearchScope);

    const locationTrackDescriptions = await getLocationTrackDescriptions(
        searchResult.locationTracks.map((lt) => lt.id),
        layoutContext,
    );

    const locationTrackOptions = searchResult.locationTracks.map((locationTrack) => {
        const description =
            locationTrackDescriptions?.find((d) => d.id == locationTrack.id)?.description ?? '';

        return createLocationTrackOptionItem(locationTrack, description);
    });

    return [
        searchResult.operatingPoints.map(createOperatingPointOptionItem),
        locationTrackOptions,
        searchResult.switches.map(createSwitchOptionItem),
        searchResult.trackNumbers.map(createTrackNumberOptionItem),
    ].flat();
}

export const ToolBar: React.FC<ToolbarParams> = ({
    onSelect,
    onUnselect,
    onLayoutContextChange,
    onOpenPreview,
    showArea,
    layoutContext,
    onStopLinking,
    splittingState,
    linkingState,
    selectingWorkspace,
    setSelectingWorkspace,
}: ToolbarParams) => {
    const { t } = useTranslation();

    const [showNewAssetMenu, setShowNewAssetMenu] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddKmPostDialog, setShowAddKmPostDialog] = React.useState(false);
    const menuRef = React.useRef(null);

    const disableNewAssetMenu =
        layoutContext.publicationState !== 'DRAFT' ||
        selectingWorkspace ||
        linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
        linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
        !!splittingState;

    enum NewMenuItems {
        'trackNumber' = 1,
        'locationTrack' = 2,
        'switch' = 3,
        'kmPost' = 4,
    }

    const newMenuItems: MenuOption[] = [
        menuOption(
            () => showAddDialog(NewMenuItems.trackNumber),
            t('tool-bar.new-track-number'),
            'tool-bar.new-track-number',
        ),
        menuOption(
            () => showAddDialog(NewMenuItems.locationTrack),
            t('tool-bar.new-location-track'),
            'tool-bar.new-location-track',
        ),
        menuOption(
            () => showAddDialog(NewMenuItems.switch),
            t('tool-bar.new-switch'),
            'tool-bar.new-switch',
        ),
        menuOption(
            () => showAddDialog(NewMenuItems.kmPost),
            t('tool-bar.new-km-post'),
            'tool-bar.new-km-post',
        ),
    ];

    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetOptions = debounceAsync(getOptions, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const memoizedDebouncedGetOptions = React.useCallback(
        (searchTerm: string) =>
            debouncedGetOptions(layoutContext, searchTerm, splittingState?.originLocationTrack?.id),
        [layoutContext, splittingState],
    );

    function onItemSelected(item: SearchItemValue | undefined) {
        if (!item) {
            return;
        }

        switch (item.type) {
            case 'operatingPointSearchItem': {
                const operatingPointArea = calculateBoundingBoxToShowAroundLocation(
                    item.operatingPoint.location,
                    MAP_POINT_OPERATING_POINT_BBOX_OFFSET,
                );

                showArea(operatingPointArea);
                return;
            }

            case 'locationTrackSearchItem':
                item.locationTrack.boundingBox && showArea(item.locationTrack.boundingBox);
                return onSelect({
                    locationTracks: [item.locationTrack.id],
                    trackNumbers: [],
                    switches: [],
                });

            case 'switchSearchItem':
                if (item.layoutSwitch.joints.length > 0) {
                    const center = centerForBoundingBox(
                        boundingBoxAroundPoints(
                            item.layoutSwitch.joints.map((joint) => joint.location),
                        ),
                    );
                    const bbox = expandBoundingBox(boundingBoxAroundPoints([center]), 200);
                    showArea(bbox);
                }
                return !splittingState
                    ? onSelect({
                          switches: [item.layoutSwitch.id],
                          locationTracks: [],
                          trackNumbers: [],
                      })
                    : undefined;

            case 'trackNumberSearchItem':
                getTrackNumberReferenceLine(item.trackNumber.id, layoutContext).then(
                    (referenceLine) => {
                        if (referenceLine?.boundingBox) {
                            showArea(referenceLine.boundingBox);
                        }
                    },
                );

                return onSelect({
                    trackNumbers: [item.trackNumber.id],
                    locationTracks: [],
                    switches: [],
                });

            default:
                return exhaustiveMatchingGuard(item);
        }
    }

    function showAddDialog(dialog: NewMenuItems) {
        switch (dialog) {
            case NewMenuItems.trackNumber:
                setShowAddTrackNumberDialog(true);
                break;
            case NewMenuItems.locationTrack:
                setShowAddLocationTrackDialog(true);
                break;
            case NewMenuItems.switch:
                setShowAddSwitchDialog(true);
                break;
            case NewMenuItems.kmPost:
                setShowAddKmPostDialog(true);
                break;
            default:
                return exhaustiveMatchingGuard(dialog);
        }
    }

    function switchToMainOfficial() {
        onLayoutContextChange({ publicationState: 'OFFICIAL', branch: 'MAIN' });
        setShowNewAssetMenu(false);
        setSelectingWorkspace(false);
    }

    const switchToMainDraft = () => {
        onLayoutContextChange({
            publicationState: 'DRAFT',
            branch: 'MAIN',
        });
        setSelectingWorkspace(false);
    };

    function openPreviewAndStopLinking() {
        onOpenPreview();
        onStopLinking();
    }

    const layoutContextDraft = draftLayoutContext(layoutContext);
    const handleTrackNumberSave = refreshTrackNumberSelection(
        layoutContextDraft,
        onSelect,
        onUnselect,
    );
    const handleLocationTrackSave = refreshLocationTrackSelection(
        layoutContextDraft,
        onSelect,
        onUnselect,
    );
    const handleSwitchSave = refreshSwitchSelection(layoutContextDraft, onSelect, onUnselect);
    const handleKmPostSave = refereshKmPostSelection(layoutContextDraft, onSelect, onUnselect);

    const modeNavigationButtonsDisabledReason = () => {
        if (splittingState) {
            return t('tool-bar.splitting-in-progress');
        } else if (linkingState?.state === 'allSet' || linkingState?.state === 'setup') {
            return t('tool-bar.linking-in-progress');
        } else {
            return undefined;
        }
    };

    const className = createClassName(
        'tool-bar',
        !layoutContext.branch && `tool-bar--${layoutContext.publicationState.toLowerCase()}`,
        (layoutContext.branch || selectingWorkspace) && `tool-bar--design`,
    );

    return (
        <div className={className}>
            <div className={styles['tool-bar__left-section']}>
                <span className={styles['tool-bar__tabs']}>
                    <TabHeader
                        className={styles['tool-bar__tab-header']}
                        qaId="current-mode-tab"
                        selected={
                            layoutContext.branch === 'MAIN' &&
                            !selectingWorkspace &&
                            layoutContext.publicationState === 'OFFICIAL'
                        }
                        onClick={() => switchToMainOfficial()}>
                        {t('tool-bar.current-mode')}
                    </TabHeader>
                    <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                        <TabHeader
                            className={styles['tool-bar__tab-header']}
                            qaId={'draft-mode-tab'}
                            selected={
                                layoutContext.branch === 'MAIN' &&
                                !selectingWorkspace &&
                                layoutContext.publicationState === 'DRAFT'
                            }
                            onClick={() => switchToMainDraft()}>
                            {t('tool-bar.draft-mode')}
                        </TabHeader>
                    </PrivilegeRequired>
                    <EnvRestricted restrictTo={'test'}>
                        <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                            <TabHeader
                                className={styles['tool-bar__tab-header']}
                                qaId={'design-mode-tab'}
                                selected={layoutContext.branch !== 'MAIN' || selectingWorkspace}
                                onClick={() => setSelectingWorkspace(true)}>
                                {t('tool-bar.design-mode')}
                            </TabHeader>
                        </PrivilegeRequired>
                    </EnvRestricted>
                </span>
                <Dropdown
                    placeholder={
                        splittingState
                            ? t('tool-bar.search-from-track', {
                                  track: splittingState.originLocationTrack.name,
                              })
                            : t('tool-bar.search-from-whole-network')
                    }
                    disabled={selectingWorkspace}
                    options={memoizedDebouncedGetOptions}
                    searchable
                    onChange={onItemSelected}
                    size={DropdownSize.STRETCH}
                    wideList
                    wide
                    qa-id="search-box"
                />
            </div>
            <div className={styles['tool-bar__right-section']}>
                {layoutContext.publicationState === 'DRAFT' && (
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <div className={styles['tool-bar__new-menu-button']} qa-id={'tool-bar.new'}>
                            <Button
                                ref={menuRef}
                                title={t('tool-bar.new')}
                                variant={ButtonVariant.GHOST}
                                icon={Icons.Append}
                                disabled={disableNewAssetMenu}
                                onClick={() => setShowNewAssetMenu(!showNewAssetMenu)}
                            />
                        </div>
                    </PrivilegeRequired>
                )}
                {(layoutContext.branch !== 'MAIN' || selectingWorkspace) && (
                    <WorkspaceSelectionContainer
                        selectingWorkspace={selectingWorkspace}
                        setSelectingWorkspace={setSelectingWorkspace}
                    />
                )}
                {layoutContext.publicationState == 'DRAFT' && (
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <Button
                            disabled={selectingWorkspace || !!splittingState || !!linkingState}
                            variant={ButtonVariant.PRIMARY}
                            title={modeNavigationButtonsDisabledReason()}
                            qa-id="open-preview-view"
                            onClick={() => openPreviewAndStopLinking()}>
                            {t('tool-bar.preview-mode.enable')}
                        </Button>
                    </PrivilegeRequired>
                )}
            </div>

            {showNewAssetMenu && (
                <Menu
                    positionRef={menuRef}
                    items={newMenuItems}
                    onClickOutside={() => setShowNewAssetMenu(false)}
                    onClose={() => setShowNewAssetMenu(false)}
                />
            )}

            {showAddTrackNumberDialog && (
                <TrackNumberEditDialogContainer
                    onClose={() => setShowAddTrackNumberDialog(false)}
                    onSave={handleTrackNumberSave}
                />
            )}
            {showAddLocationTrackDialog && (
                <LocationTrackEditDialogContainer
                    onClose={() => setShowAddLocationTrackDialog(false)}
                    onSave={handleLocationTrackSave}
                />
            )}

            {showAddSwitchDialog && (
                <SwitchEditDialogContainer
                    onClose={() => setShowAddSwitchDialog(false)}
                    onSave={handleSwitchSave}
                />
            )}

            {showAddKmPostDialog && (
                <KmPostEditDialogContainer
                    onClose={() => setShowAddKmPostDialog(false)}
                    onSave={handleKmPostSave}
                />
            )}
        </div>
    );
};
