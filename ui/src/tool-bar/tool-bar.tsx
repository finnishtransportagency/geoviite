import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    Button,
    ButtonIconPosition,
    ButtonSize,
    ButtonVariant,
} from 'vayla-design-lib/button/button';
import { Dropdown, dropdownOption, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
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
import {
    draftLayoutContext,
    LayoutContext,
    LayoutContextMode,
    LayoutDesignId,
} from 'common/common-model';
import { TabHeader } from 'geoviite-design-lib/tab-header/tab-header';
import { createClassName } from 'vayla-design-lib/utils';
import { EnvRestricted } from 'environment/env-restricted';
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_OPERATING_POINT_BBOX_OFFSET,
} from 'map/map-utils';
import { DesignSelectionContainer } from 'tool-bar/workspace-selection';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getLayoutDesign, updateLayoutDesign } from 'track-layout/layout-design-api';
import { getChangeTimes, updateLayoutDesignChangeTime } from 'common/change-time-api';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { WorkspaceDeleteConfirmDialog } from 'tool-bar/workspace-delete-confirm-dialog';
import { ALIGNMENT_DESCRIPTION_REGEX } from 'tool-panel/location-track/dialog/location-track-validation';

export type ToolbarParams = {
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onOpenPreview: () => void;
    showArea: (area: BoundingBox) => void;
    layoutContext: LayoutContext;
    onStopLinking: () => void;
    onMapLayerChange: (change: MapLayerMenuChange) => void;
    mapLayerMenuGroups: MapLayerMenuGroups;
    visibleLayers: MapLayerName[];
    splittingState: SplittingState | undefined;
    linkingState: LinkingState | undefined;
    layoutContextMode: LayoutContextMode;
    onLayoutContextModeChange: (value: LayoutContextMode) => void;
    designId: LayoutDesignId | undefined;
    onDesignIdChange: (value: LayoutDesignId | undefined) => void;
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

// The characters that alignment descriptions can contain is a superset of the characters that can be used in search,
// and it's considered quite likely to stay that way even if allowed character sets for names etc. are changed.
const SEARCH_REGEX = ALIGNMENT_DESCRIPTION_REGEX;

async function getOptions(
    layoutContext: LayoutContext,
    searchTerm: string,
    locationTrackSearchScope: LocationTrackId | undefined,
): Promise<Item<SearchItemValue>[]> {
    if (isNilOrBlank(searchTerm) || !searchTerm.match(SEARCH_REGEX)) {
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
    onOpenPreview,
    showArea,
    layoutContext,
    onStopLinking,
    splittingState,
    linkingState,
    layoutContextMode,
    onLayoutContextModeChange,
    designId,
    onDesignIdChange,
}: ToolbarParams) => {
    const { t } = useTranslation();

    const [showNewAssetMenu, setShowNewAssetMenu] = React.useState(false);
    const [showAddTrackNumberDialog, setShowAddTrackNumberDialog] = React.useState(false);
    const [showAddSwitchDialog, setShowAddSwitchDialog] = React.useState(false);
    const [showAddLocationTrackDialog, setShowAddLocationTrackDialog] = React.useState(false);
    const [showAddKmPostDialog, setShowAddKmPostDialog] = React.useState(false);
    const [showEditWorkspaceDialog, setShowEditWorkspaceDialog] = React.useState(false);
    const [showDeleteWorkspaceDialog, setShowDeleteWorkspaceDialog] = React.useState(false);
    const [designIdSelectorOpened, setDesignIdSelectorOpened] = React.useState(false);
    const [savingWorkspace, setSavingWorkspace] = React.useState(false);
    const menuRef = React.useRef(null);
    const designIdSelectorRef = React.useRef(null);

    const [currentDesign, designLoadStatus] = useLoaderWithStatus(
        () => designId && getLayoutDesign(getChangeTimes().layoutDesign, designId),
        [getChangeTimes().layoutDesign, designId],
    );
    const currentDesignExists =
        designLoadStatus === LoaderStatus.Ready && currentDesign !== undefined;

    const disableNewAssetMenu =
        layoutContext.publicationState !== 'DRAFT' ||
        (layoutContextMode === 'DESIGN' && !currentDesignExists) ||
        linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
        linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
        !!splittingState;

    const showDesignIdSelector =
        designIdSelectorOpened ||
        (layoutContextMode === 'DESIGN' &&
            !currentDesign &&
            designLoadStatus === LoaderStatus.Ready);

    const canEnterPreview =
        layoutContext.publicationState === 'DRAFT' && !splittingState && !linkingState;

    const canSearch = layoutContextMode !== 'DESIGN' || currentDesignExists;

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
        onLayoutContextModeChange('MAIN-OFFICIAL');
    }

    const switchToMainDraft = () => {
        onLayoutContextModeChange('MAIN-DRAFT');
    };

    function switchToDesign() {
        onLayoutContextModeChange('DESIGN');
    }

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

    const layoutContextTransferDisabledReason = splittingState
        ? t('tool-bar.splitting-in-progress')
        : undefined;

    const modeNavigationButtonsDisabledReason = () => {
        if (splittingState) {
            return t('tool-bar.splitting-in-progress');
        } else if (linkingState?.state === 'allSet' || linkingState?.state === 'setup') {
            return t('tool-bar.linking-in-progress');
        } else {
            return undefined;
        }
    };

    const newMenuTooltip = splittingState ? t('tool-bar.splitting-in-progress') : t('tool-bar.new');

    const className = createClassName(
        'tool-bar',
        layoutContextMode === 'MAIN-OFFICIAL' && 'tool-bar--official',
        layoutContextMode === 'MAIN-DRAFT' && 'tool-bar--draft',
        layoutContextMode === 'DESIGN' && 'tool-bar--design',
    );

    const unselectDesign = () => {
        onDesignIdChange(undefined);
    };

    return (
        <div className={className}>
            <div className={styles['tool-bar__left-section']}>
                <span className={styles['tool-bar__tabs']}>
                    <TabHeader
                        className={styles['tool-bar__tab-header']}
                        qaId="current-mode-tab"
                        selected={layoutContextMode === 'MAIN-OFFICIAL'}
                        title={layoutContextTransferDisabledReason}
                        disabled={!!splittingState}
                        onClick={() => switchToMainOfficial()}>
                        {t('tool-bar.current-mode')}
                    </TabHeader>
                    <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                        <TabHeader
                            className={styles['tool-bar__tab-header']}
                            qaId={'draft-mode-tab'}
                            selected={layoutContextMode === 'MAIN-DRAFT'}
                            onClick={() => switchToMainDraft()}>
                            {t('tool-bar.draft-mode')}
                        </TabHeader>
                    </PrivilegeRequired>
                    <EnvRestricted restrictTo={'test'}>
                        <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                            <div>
                                <TabHeader
                                    className={styles['tool-bar__tab-header']}
                                    qaId={'design-mode-tab'}
                                    selected={layoutContextMode === 'DESIGN'}
                                    onClick={switchToDesign}
                                    title={layoutContextTransferDisabledReason}
                                    disabled={!!splittingState}>
                                    <div className={styles['tool-bar__design-tab-content']}>
                                        {t('tool-bar.design-mode')}
                                        <span>{currentDesign && `:`}</span>
                                        <div className={styles['tool-bar__design-tab-actions']}>
                                            <Button
                                                variant={ButtonVariant.GHOST}
                                                size={ButtonSize.SMALL}
                                                icon={Icons.Down}
                                                iconPosition={ButtonIconPosition.END}
                                                disabled={!!splittingState}
                                                inheritTypography={true}
                                                onClick={(e) => {
                                                    e.stopPropagation(); // otherwise layout selection gets the click
                                                    switchToDesign();
                                                    setDesignIdSelectorOpened(
                                                        !designIdSelectorOpened,
                                                    );
                                                }}
                                                qa-id={'workspace-selection-dropdown-toggle'}>
                                                {currentDesign && (
                                                    <span
                                                        className={styles['tool-bar__design-name']}>
                                                        {currentDesign.name}
                                                    </span>
                                                )}
                                            </Button>
                                            <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                                {layoutContextMode === 'DESIGN' &&
                                                    currentDesign && (
                                                        <React.Fragment>
                                                            <Button
                                                                variant={ButtonVariant.GHOST}
                                                                size={ButtonSize.SMALL}
                                                                icon={Icons.Edit}
                                                                onClick={() =>
                                                                    setShowEditWorkspaceDialog(true)
                                                                }
                                                                qa-id={'workspace-edit-button'}
                                                            />
                                                            <Button
                                                                variant={ButtonVariant.GHOST}
                                                                size={ButtonSize.SMALL}
                                                                icon={Icons.Delete}
                                                                onClick={() =>
                                                                    setShowDeleteWorkspaceDialog(
                                                                        true,
                                                                    )
                                                                }
                                                                qa-id={'workspace-delete-button'}
                                                            />
                                                        </React.Fragment>
                                                    )}
                                            </PrivilegeRequired>
                                        </div>
                                    </div>
                                </TabHeader>

                                {showDesignIdSelector && (
                                    <div ref={designIdSelectorRef}>
                                        <CloseableModal
                                            positionRef={designIdSelectorRef}
                                            onClickOutside={() => setDesignIdSelectorOpened(false)}
                                            className={styles['tool-bar__design-id-selector-popup']}
                                            offsetX={0}
                                            offsetY={0}>
                                            <DesignSelectionContainer
                                                onDesignIdChange={() =>
                                                    setDesignIdSelectorOpened(false)
                                                }
                                            />
                                        </CloseableModal>
                                    </div>
                                )}
                            </div>
                        </PrivilegeRequired>
                    </EnvRestricted>
                </span>
            </div>
            <div className={styles['tool-bar__right-section']}>
                {layoutContext.publicationState === 'DRAFT' && (
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <div className={styles['tool-bar__new-menu-button']} qa-id={'tool-bar.new'}>
                            <Button
                                ref={menuRef}
                                title={newMenuTooltip}
                                variant={ButtonVariant.GHOST}
                                icon={Icons.Append}
                                disabled={disableNewAssetMenu}
                                onClick={() => setShowNewAssetMenu(!showNewAssetMenu)}
                            />
                        </div>
                    </PrivilegeRequired>
                )}
                {layoutContext.publicationState == 'DRAFT' && (
                    <PrivilegeRequired privilege={EDIT_LAYOUT}>
                        <Button
                            disabled={!canEnterPreview}
                            variant={ButtonVariant.PRIMARY}
                            title={modeNavigationButtonsDisabledReason()}
                            qa-id="open-preview-view"
                            onClick={() => openPreviewAndStopLinking()}>
                            {t('tool-bar.preview-mode.enable')}
                        </Button>
                    </PrivilegeRequired>
                )}
                <div className={styles['tool-bar__search-container']}>
                    <Dropdown
                        placeholder={
                            splittingState
                                ? t('tool-bar.search-from-track', {
                                      track: splittingState.originLocationTrack.name,
                                  })
                                : t('tool-bar.search-from-whole-network')
                        }
                        disabled={!canSearch}
                        options={memoizedDebouncedGetOptions}
                        searchable
                        onChange={onItemSelected}
                        size={DropdownSize.STRETCH}
                        wideList
                        wide
                        qa-id="search-box"
                    />
                </div>
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
                    editType={'CREATE'}
                />
            )}

            {showEditWorkspaceDialog && (
                <WorkspaceDialog
                    existingDesign={currentDesign}
                    onCancel={() => setShowEditWorkspaceDialog(false)}
                    onSave={(_, request) => {
                        if (currentDesign) {
                            setSavingWorkspace(true);
                            updateLayoutDesign(currentDesign.id, request)
                                .then(() => {
                                    setShowEditWorkspaceDialog(false);
                                })
                                .finally(() => {
                                    updateLayoutDesignChangeTime();
                                    setSavingWorkspace(false);
                                });
                        }
                    }}
                    saving={savingWorkspace}
                />
            )}

            {showDeleteWorkspaceDialog && currentDesign && (
                <WorkspaceDeleteConfirmDialog
                    closeDialog={() => setShowDeleteWorkspaceDialog(false)}
                    currentDesign={currentDesign}
                    onDesignDeleted={unselectDesign}
                />
            )}
        </div>
    );
};
