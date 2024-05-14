import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Dropdown, DropdownSize, Item } from 'vayla-design-lib/dropdown/dropdown';
import { getLocationTrackDescriptions } from 'track-layout/layout-location-track-api';
import {
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutTrackNumber,
    LocationTrackId,
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
import { Menu, MenuOption, menuValueOption } from 'vayla-design-lib/menu/menu';
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
import { EDIT_LAYOUT } from 'user/user-model';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { TabHeader } from 'geoviite-design-lib/tab-header/tab-header';
import { createClassName } from 'vayla-design-lib/utils';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { EnvRestricted } from 'environment/env-restricted';

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

type SwitchItemValue = {
    layoutSwitch: LayoutSwitch;
    type: 'switchSearchItem';
};

type TrackNumberItemValue = {
    trackNumber: LayoutTrackNumber;
    type: 'trackNumberSearchItem';
};

function createTrackNumberItem(layoutTrackNumber: LayoutTrackNumber): Item<TrackNumberItemValue> {
    return menuValueOption(
        {
            type: 'trackNumberSearchItem',
            trackNumber: layoutTrackNumber,
        } as const,
        layoutTrackNumber.number,
        `track-number-${layoutTrackNumber.id}`,
    );
}

type SearchItemValue = LocationTrackItemValue | SwitchItemValue | TrackNumberItemValue;

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

    const locationTracks: Item<LocationTrackItemValue>[] = searchResult.locationTracks.map(
        (locationTrack) =>
            menuValueOption(
                {
                    type: 'locationTrackSearchItem',
                    locationTrack: locationTrack,
                } as const,
                `${locationTrack.name}, ${
                    (locationTrackDescriptions &&
                        locationTrackDescriptions.find((d) => d.id == locationTrack.id)
                            ?.description) ??
                    ''
                }`,
                `location-track-${locationTrack.id}`,
            ),
    );

    const switches: Item<SwitchItemValue>[] = searchResult.switches.map((layoutSwitch) =>
        menuValueOption(
            {
                type: 'switchSearchItem',
                layoutSwitch: layoutSwitch,
            } as const,
            layoutSwitch.name,
            `switch-${layoutSwitch.id}`,
        ),
    );

    const trackNumbers: Item<TrackNumberItemValue>[] =
        searchResult.trackNumbers.map(createTrackNumberItem);

    return await Promise.all([locationTracks, switches, trackNumbers]).then((results) =>
        results.flat(),
    );
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
    const [showCreateWorkspaceDialog, setShowCreateWorkspaceDialog] = React.useState(false);
    const menuRef = React.useRef(null);
    const selectWorkspaceDropdownRef = React.useRef<HTMLInputElement>(null);

    React.useEffect(() => {
        if (selectingWorkspace) selectWorkspaceDropdownRef?.current?.focus();
    }, [selectingWorkspace]);

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

    const newMenuItems: MenuOption<NewMenuItems>[] = [
        menuValueOption(
            NewMenuItems.trackNumber,
            t('tool-bar.new-track-number'),
            'tool-bar.new-track-number',
        ),
        menuValueOption(
            NewMenuItems.locationTrack,
            t('tool-bar.new-location-track'),
            'tool-bar.new-location-track',
        ),
        menuValueOption(NewMenuItems.switch, t('tool-bar.new-switch'), 'tool-bar.new-switch'),
        menuValueOption(NewMenuItems.kmPost, t('tool-bar.new-km-post'), 'tool-bar.new-km-post'),
    ];

    const handleNewMenuItemChange = (item: NewMenuItems) => {
        showAddDialog(item);
    };

    // Use debounced function to collect keystrokes before triggering a search
    const debouncedGetOptions = debounceAsync(getOptions, 250);
    // Use memoized function to make debouncing functionality to work when re-rendering
    const memoizedDebouncedGetOptions = React.useCallback(
        (searchTerm: string) =>
            debouncedGetOptions(layoutContext, searchTerm, splittingState?.originLocationTrack?.id),
        [layoutContext, splittingState],
    );

    function onItemSelected(item: SearchItemValue | undefined) {
        switch (item?.type) {
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
                return;
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

        setShowNewAssetMenu(false);
    }

    function switchToMainOfficial() {
        onLayoutContextChange({ publicationState: 'OFFICIAL', designId: undefined });
        setShowNewAssetMenu(false);
        setSelectingWorkspace(false);
    }

    const switchToMainDraft = () => {
        onLayoutContextChange({
            publicationState: 'DRAFT',
            designId: undefined,
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
        !layoutContext.designId && `tool-bar--${layoutContext.publicationState.toLowerCase()}`,
        layoutContext.designId || (selectingWorkspace && `tool-bar--design`),
    );

    return (
        <div className={className}>
            <div className={styles['tool-bar__left-section']}>
                <span className={styles['tool-bar__tabs']}>
                    <TabHeader
                        className={styles['tool-bar__tab-header']}
                        qaId="current-mode-tab"
                        selected={
                            !layoutContext.designId &&
                            !selectingWorkspace &&
                            layoutContext.publicationState === 'OFFICIAL'
                        }
                        onClick={() => switchToMainOfficial()}>
                        {t('tool-bar.current-mode')}
                    </TabHeader>
                    <TabHeader
                        className={styles['tool-bar__tab-header']}
                        qaId={'draft-mode-tab'}
                        selected={
                            !layoutContext.designId &&
                            !selectingWorkspace &&
                            layoutContext.publicationState === 'DRAFT'
                        }
                        onClick={() => switchToMainDraft()}>
                        {t('tool-bar.draft-mode')}
                    </TabHeader>
                    <EnvRestricted restrictTo={'test'}>
                        <TabHeader
                            className={styles['tool-bar__tab-header']}
                            qaId={'design-mode-tab'}
                            selected={!!layoutContext.designId || selectingWorkspace}
                            onClick={() => setSelectingWorkspace(true)}>
                            {t('tool-bar.design-mode')}
                        </TabHeader>
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
                {!layoutContext.designId && layoutContext.publicationState === 'DRAFT' && (
                    <React.Fragment>
                        <div className={styles['tool-bar__new-menu-button']} qa-id={'tool-bar.new'}>
                            <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                <Button
                                    ref={menuRef}
                                    title={t('tool-bar.new')}
                                    variant={ButtonVariant.GHOST}
                                    icon={Icons.Append}
                                    disabled={disableNewAssetMenu}
                                    onClick={() => setShowNewAssetMenu(!showNewAssetMenu)}
                                />
                            </PrivilegeRequired>
                        </div>
                    </React.Fragment>
                )}
                {(layoutContext.designId || selectingWorkspace) && (
                    <React.Fragment>
                        {/* TODO Add functionality once design projects have a data model */}
                        <Dropdown
                            inputRef={selectWorkspaceDropdownRef}
                            placeholder={t('tool-bar.choose-workspace')}
                            onAddClick={() => setShowCreateWorkspaceDialog(true)}
                        />
                        <Button
                            variant={ButtonVariant.GHOST}
                            icon={Icons.Edit}
                            disabled={!layoutContext.designId}
                        />
                        <Button
                            variant={ButtonVariant.GHOST}
                            icon={Icons.Delete}
                            disabled={!layoutContext.designId}
                        />
                    </React.Fragment>
                )}
                {layoutContext.publicationState == 'DRAFT' && (
                    <Button
                        disabled={
                            selectingWorkspace ||
                            !!splittingState ||
                            linkingState?.state === 'allSet' ||
                            linkingState?.state === 'setup'
                        }
                        variant={ButtonVariant.PRIMARY}
                        title={modeNavigationButtonsDisabledReason()}
                        qa-id="open-preview-view"
                        onClick={() => openPreviewAndStopLinking()}>
                        {t('tool-bar.preview-mode.enable')}
                    </Button>
                )}
            </div>

            {showNewAssetMenu && (
                <Menu
                    positionRef={menuRef}
                    items={newMenuItems}
                    onSelect={(item) => item && handleNewMenuItemChange(item)}
                    onClickOutside={() => setShowNewAssetMenu(false)}
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

            {showCreateWorkspaceDialog && (
                <WorkspaceDialog
                    onCancel={() => {
                        setShowCreateWorkspaceDialog(false);
                        setSelectingWorkspace(false);
                    }}
                    onSave={() => setShowCreateWorkspaceDialog(false)}
                />
            )}
        </div>
    );
};
