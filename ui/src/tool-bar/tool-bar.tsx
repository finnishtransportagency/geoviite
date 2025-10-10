import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    Button,
    ButtonIconPosition,
    ButtonSize,
    ButtonVariant,
} from 'vayla-design-lib/button/button';
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
    refreshOperationalPointSelection,
    refreshSwitchSelection,
    refreshTrackNumberSelection,
} from 'track-layout/track-layout-react-utils';
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
import {
    calculateBoundingBoxToShowAroundLocation,
    MAP_POINT_OPERATIONAL_POINT_BBOX_OFFSET,
} from 'map/map-utils';
import { DesignSelectionContainer } from 'tool-bar/workspace-selection';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import {
    getLayoutDesign,
    LayoutDesignSaveRequest,
    updateLayoutDesign,
} from 'track-layout/layout-design-api';
import { getChangeTimes, updateLayoutDesignChangeTime } from 'common/change-time-api';
import { WorkspaceDialog } from 'tool-bar/workspace-dialog';
import { WorkspaceDeleteConfirmDialog } from 'tool-bar/workspace-delete-confirm-dialog';
import { SearchDropdown, SearchItemType, SearchItemValue } from 'asset-search/search-dropdown';
import { ToolPanelAsset } from 'tool-panel/tool-panel';
import { error } from 'geoviite-design-lib/snackbar/snackbar';
import { InternalOperationalPointEditDialog } from 'tool-panel/operational-point/internal-operational-point-edit-dialog';

const DESIGN_SELECT_POPUP_MARGIN_WHEN_SELECTED = 6;
const DESIGN_SELECT_POPUP_MARGIN_WHEN_NOT_SELECTED = 3;

export type ToolbarParams = {
    onSelect: OnSelectFunction;
    setToolPanelTab: (tab: ToolPanelAsset) => void;
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

export const ToolBar: React.FC<ToolbarParams> = ({
    onSelect,
    setToolPanelTab,
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
    const [showAddOperationalPointDialog, setShowAddOperationalPointDialog] = React.useState(false);
    const [showEditWorkspaceDialog, setShowEditWorkspaceDialog] = React.useState(false);
    const [showDeleteWorkspaceDialog, setShowDeleteWorkspaceDialog] = React.useState(false);
    const [designIdSelectorOpened, setDesignIdSelectorOpened] = React.useState(false);
    const [savingWorkspace, setSavingWorkspace] = React.useState(false);
    const menuRef = React.useRef(null);
    const designSelectButtonRef = React.useRef(null);
    const designTabRef = React.useRef(null);

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
        'operationalPoint' = 5,
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
        menuOption(
            () => showAddDialog(NewMenuItems.operationalPoint),
            t('tool-bar.new-operational-point'),
            'tool-bar.new-operational-point',
        ),
    ];

    function onItemSelected(item: SearchItemValue<SearchItemType> | undefined) {
        if (!item) {
            return;
        }

        switch (item.type) {
            case SearchItemType.OPERATIONAL_POINT: {
                const operationalPointArea = item.operationalPoint.location
                    ? calculateBoundingBoxToShowAroundLocation(
                          item.operationalPoint.location,
                          MAP_POINT_OPERATIONAL_POINT_BBOX_OFFSET,
                      )
                    : undefined;

                if (operationalPointArea) {
                    showArea(operationalPointArea);
                }
                break;
            }

            case SearchItemType.LOCATION_TRACK:
                item.locationTrack.boundingBox && showArea(item.locationTrack.boundingBox);

                onSelect({
                    locationTracks: [item.locationTrack.id],
                    trackNumbers: [],
                    switches: [],
                });
                setToolPanelTab({ id: item.locationTrack.id, type: 'LOCATION_TRACK' });
                break;

            case SearchItemType.SWITCH:
                if (item.layoutSwitch.joints.length > 0) {
                    const center = centerForBoundingBox(
                        boundingBoxAroundPoints(
                            item.layoutSwitch.joints.map((joint) => joint.location),
                        ),
                    );
                    const bbox = expandBoundingBox(boundingBoxAroundPoints([center]), 200);
                    showArea(bbox);
                }
                if (!splittingState) {
                    onSelect({
                        switches: [item.layoutSwitch.id],
                        locationTracks: [],
                        trackNumbers: [],
                    });
                }
                setToolPanelTab({ id: item.layoutSwitch.id, type: 'SWITCH' });
                break;

            case SearchItemType.TRACK_NUMBER:
                getTrackNumberReferenceLine(item.trackNumber.id, layoutContext).then(
                    (referenceLine) => {
                        if (referenceLine?.boundingBox) {
                            showArea(referenceLine.boundingBox);
                        }
                    },
                );

                onSelect({
                    trackNumbers: [item.trackNumber.id],
                    locationTracks: [],
                    switches: [],
                });
                setToolPanelTab({ id: item.trackNumber.id, type: 'TRACK_NUMBER' });
                break;
            case SearchItemType.KM_POST:
                error(t('unsupported-result-type', { type: item.type }));
                break;

            default:
                exhaustiveMatchingGuard(item);
                break;
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
            case NewMenuItems.operationalPoint:
                setShowAddOperationalPointDialog(true);
                break;
            default:
                return exhaustiveMatchingGuard(dialog);
        }
    }

    function switchToMainOfficial() {
        onLayoutContextModeChange('MAIN_OFFICIAL');
    }

    const switchToMainDraft = () => {
        onLayoutContextModeChange('MAIN_DRAFT');
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
    const handleOperationalPointSave = refreshOperationalPointSelection(
        layoutContextDraft,
        onSelect,
        onUnselect,
    );

    const layoutContextTransferDisabledReason = (): string | undefined => {
        if (splittingState) return t('tool-bar.splitting-in-progress');
        else if (linkingState) return t('tool-bar.linking-in-progress');
        return undefined;
    };

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
        layoutContextMode === 'MAIN_OFFICIAL' && 'tool-bar--official',
        layoutContextMode === 'MAIN_DRAFT' && 'tool-bar--draft',
        layoutContextMode === 'DESIGN' && 'tool-bar--design',
    );

    const unselectDesign = () => {
        onDesignIdChange(undefined);
    };

    const onSaveDesign = (
        _: LayoutDesignId | undefined,
        request: LayoutDesignSaveRequest,
    ): void => {
        if (currentDesign) {
            setSavingWorkspace(true);
            updateLayoutDesign(currentDesign.id, request)
                .then(() => {
                    setShowEditWorkspaceDialog(false);
                })
                .finally(() => {
                    updateLayoutDesignChangeTime();
                    setSavingWorkspace(false);
                    Snackbar.success('workspace-dialog.update-success');
                });
        }
    };

    return (
        <div className={className}>
            <div className={styles['tool-bar__left-section']}>
                <span className={styles['tool-bar__tabs']}>
                    <TabHeader
                        qaId="current-mode-tab"
                        selected={layoutContextMode === 'MAIN_OFFICIAL'}
                        title={layoutContextTransferDisabledReason()}
                        disabled={!!splittingState || !!linkingState}
                        onClick={() => switchToMainOfficial()}>
                        <span>{t(`enum.LayoutContextMode.MAIN_OFFICIAL`)}</span>
                    </TabHeader>
                    <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                        <TabHeader
                            qaId={'draft-mode-tab'}
                            selected={layoutContextMode === 'MAIN_DRAFT'}
                            title={layoutContextTransferDisabledReason()}
                            disabled={!!splittingState || !!linkingState}
                            onClick={() => switchToMainDraft()}>
                            <span>{t(`enum.LayoutContextMode.MAIN_DRAFT`)}</span>
                        </TabHeader>
                    </PrivilegeRequired>
                    <PrivilegeRequired privilege={VIEW_LAYOUT_DRAFT}>
                        <div ref={designTabRef}>
                            <TabHeader
                                qaId={'design-mode-tab'}
                                selected={layoutContextMode === 'DESIGN'}
                                title={layoutContextTransferDisabledReason()}
                                disabled={!!splittingState || !!linkingState}
                                onClick={switchToDesign}>
                                <span className={styles['tool-bar__tab-content']}>
                                    {t(`enum.LayoutContextMode.DESIGN`)}
                                    <span>{currentDesign && `:`}</span>
                                    <div className={styles['tool-bar__design-tab-actions']}>
                                        <Button
                                            className={styles['tool-bar__design-select-button']}
                                            title={currentDesign?.name}
                                            variant={ButtonVariant.GHOST}
                                            icon={Icons.Down}
                                            iconPosition={ButtonIconPosition.END}
                                            disabled={!!splittingState || !!linkingState}
                                            inheritTypography={true}
                                            ref={designSelectButtonRef}
                                            onClick={() => {
                                                switchToDesign();
                                                setDesignIdSelectorOpened(!designIdSelectorOpened);
                                            }}
                                            qa-id={'workspace-selection-dropdown-toggle'}>
                                            {currentDesign && (
                                                <span className={styles['tool-bar__design-name']}>
                                                    {currentDesign.name}
                                                </span>
                                            )}
                                        </Button>
                                        <PrivilegeRequired privilege={EDIT_LAYOUT}>
                                            {layoutContextMode === 'DESIGN' && currentDesign && (
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
                                                            setShowDeleteWorkspaceDialog(true)
                                                        }
                                                        qa-id={'workspace-delete-button'}
                                                    />
                                                </React.Fragment>
                                            )}
                                        </PrivilegeRequired>
                                    </div>
                                </span>
                            </TabHeader>
                            {showDesignIdSelector && (
                                <CloseableModal
                                    anchorElementRef={
                                        currentDesign ? designSelectButtonRef : designTabRef
                                    }
                                    openingElementRef={designSelectButtonRef}
                                    onClickOutside={() => {
                                        setDesignIdSelectorOpened(false);
                                    }}
                                    className={styles['tool-bar__design-id-selector-popup']}
                                    margin={
                                        currentDesign
                                            ? DESIGN_SELECT_POPUP_MARGIN_WHEN_SELECTED
                                            : DESIGN_SELECT_POPUP_MARGIN_WHEN_NOT_SELECTED
                                    }>
                                    <DesignSelectionContainer
                                        onDesignIdChange={() => setDesignIdSelectorOpened(false)}
                                    />
                                </CloseableModal>
                            )}
                        </div>
                    </PrivilegeRequired>
                </span>
            </div>
            <div className={styles['tool-bar__right-section']}>
                <div ref={menuRef}>
                    {layoutContext.publicationState === 'DRAFT' && (
                        <PrivilegeRequired privilege={EDIT_LAYOUT}>
                            <div
                                className={styles['tool-bar__new-menu-button']}
                                qa-id={'tool-bar.new'}>
                                <Button
                                    title={newMenuTooltip}
                                    variant={ButtonVariant.GHOST}
                                    icon={Icons.Append}
                                    disabled={disableNewAssetMenu}
                                    onClick={() => setShowNewAssetMenu(!showNewAssetMenu)}
                                />
                            </div>
                        </PrivilegeRequired>
                    )}
                </div>
                {layoutContext.publicationState === 'DRAFT' && (
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
                    <SearchDropdown
                        layoutContext={layoutContext}
                        splittingState={splittingState}
                        placeholder={
                            splittingState
                                ? t('tool-bar.search-from-track', {
                                      track: splittingState.originLocationTrack.name,
                                  })
                                : t('tool-bar.search-from-whole-network')
                        }
                        onItemSelected={onItemSelected}
                        disabled={!canSearch}
                        searchTypes={[
                            SearchItemType.LOCATION_TRACK,
                            SearchItemType.SWITCH,
                            SearchItemType.TRACK_NUMBER,
                            SearchItemType.OPERATIONAL_POINT,
                        ]}
                        includeDeletedAssets={false}
                    />
                </div>
            </div>

            {showNewAssetMenu && (
                <Menu
                    anchorElementRef={menuRef}
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

            {showAddOperationalPointDialog && (
                <InternalOperationalPointEditDialog
                    layoutContext={layoutContext}
                    operationalPoint={undefined}
                    onSave={handleOperationalPointSave}
                    onClose={() => setShowAddOperationalPointDialog(false)}
                />
            )}

            {showEditWorkspaceDialog && (
                <WorkspaceDialog
                    existingDesign={currentDesign}
                    onCancel={() => setShowEditWorkspaceDialog(false)}
                    onSave={onSaveDesign}
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
