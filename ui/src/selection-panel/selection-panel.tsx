import * as React from 'react';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import styles from './selection-panel.scss';
import {
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumber,
    LayoutTrackNumberId,
    LocationTrackId,
    OperationalPoint,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import {
    OnSelectOptions,
    OpenPlanLayout,
    OptionalItemCollections,
    SelectableItemType,
    VisiblePlanLayout,
} from 'selection/selection-model';
import { KmPostsPanel } from 'selection-panel/km-posts-panel/km-posts-panel';
import SwitchPanel from 'selection-panel/switch-panel/switch-panel';
import { OperationalPointPanel } from 'selection-panel/operational-point-panel/operational-point-panel';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import TrackNumberPanel from 'selection-panel/track-number-panel/track-number-panel';
import {
    MapLayerMenuChange,
    MapLayerMenuItem,
    MapLayerSettingChange,
    MapLayerSettings,
    MapViewport,
} from 'map/map-model';
import {
    createEmptyItemCollections,
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
} from 'selection/selection-store';
import { LayoutBranch, LayoutContext } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LocationTracksPanel } from 'selection-panel/location-track-panel/location-tracks-panel';
import ReferenceLinesPanel from 'selection-panel/reference-line-panel/reference-lines-panel';
import SelectionPanelGeometrySection from './selection-panel-geometry-section';
import { ChangeTimes } from 'common/common-slice';
import { Eye } from 'geoviite-design-lib/eye/eye';
import { TrackNumberColorKey } from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT, VIEW_GEOMETRY } from 'user/user-model';
import { objectEntries } from 'utils/array-utils';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';
import { PlanSource } from 'geometry/geometry-model';
import { FixSwitchNamesDialog } from 'selection-panel/switch-panel/fix-switch-names-dialog';
import { previewSwitchNameFixes, SwitchNameFixPreview } from 'track-layout/layout-switch-api';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Menu, menuOption } from 'vayla-design-lib/menu/menu';

type SelectionPanelProps = {
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    selectedItems: OptionalItemCollections;
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    kmPosts: LayoutKmPost[];
    referenceLines: LayoutReferenceLine[];
    locationTracks: LayoutLocationTrack[];
    switchCount: number;
    switches: LayoutSwitch[];
    operationalPoints: OperationalPoint[];
    viewport: MapViewport;
    onSelect: (options: OnSelectOptions) => void;
    selectableItemTypes: SelectableItemType[];
    onTogglePlanVisibility: (payload: VisiblePlanLayout) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
    onMapLayerSettingChange: (change: MapLayerSettingChange) => void;
    mapLayerSettings: MapLayerSettings;
    onMapLayerMenuItemChange: (change: MapLayerMenuChange) => void;
    mapLayoutMenu: MapLayerMenuItem[];
    splittingState: SplittingState | undefined;
    grouping: GeometryPlanGrouping;
    visibleSources: PlanSource[];
    planDownloadPopupOpen: boolean;
    togglePlanDownloadPopupOpen: (payload: boolean) => void;
};

const SelectionPanel: React.FC<SelectionPanelProps> = ({
    layoutContext,
    changeTimes,
    selectedItems,
    openPlans,
    visiblePlans,
    kmPosts,
    referenceLines,
    locationTracks,
    switchCount,
    switches,
    operationalPoints,
    viewport,
    onTogglePlanVisibility,
    onToggleAlignmentVisibility,
    onToggleSwitchVisibility,
    onToggleKmPostVisibility,
    onSelect,
    selectableItemTypes,
    togglePlanOpen,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
    onMapLayerSettingChange,
    mapLayerSettings,
    onMapLayerMenuItemChange,
    mapLayoutMenu,
    splittingState,
    grouping,
    visibleSources,
    planDownloadPopupOpen,
    togglePlanDownloadPopupOpen,
}: SelectionPanelProps) => {
    const { t } = useTranslation();
    const [visibleTrackNumbers, setVisibleTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    const [fixNamesDialogOpen, setFixNamesDialogOpen] = React.useState(false);
    const [fixNamesPreviews, setFixNamesPreviews] = React.useState<SwitchNameFixPreview[]>([]);
    const [showSwitchMenu, setShowSwitchMenu] = React.useState(false);
    const switchMenuRef = React.useRef<HTMLButtonElement>(null);

    const switchMenuOptions = [
        menuOption(
            () => {
                setShowSwitchMenu(false);
                handleOpenFixNamesDialog();
            },
            t('fix-switch-names.menu-item', { count: switchCount }),
            'fix-switch-names',
        ),
    ];

    const handleOpenFixNamesDialog = async () => {
        if (!viewport.area) {
            return;
        }
        const previews = await previewSwitchNameFixes(viewport.area, layoutContext);
        if (previews.length > 0) {
            setFixNamesPreviews(previews);
            setFixNamesDialogOpen(true);
        } else {
            Snackbar.success(t('fix-switch-names.no-fixes-needed'));
        }
    };

    const handleCloseFixNamesDialog = () => {
        setFixNamesDialogOpen(false);
    };

    const diagramLayerSettings = mapLayerSettings['track-number-diagram-layer'];
    const diagramLayerMenuItem = mapLayoutMenu.find((i) => i.name === 'track-number-diagram');

    const selectedTrackNumberIds: LayoutTrackNumberId[] = objectEntries(diagramLayerSettings)
        .filter(([_, setting]) => setting.selected)
        .map(([id]) => id);

    const onTrackNumberSelection = (trackNumber: LayoutTrackNumber) => {
        onMapLayerSettingChange({
            name: 'track-number-diagram-layer',
            settings: {
                ...diagramLayerSettings,
                [trackNumber.id]: {
                    ...diagramLayerSettings[trackNumber.id],
                    selected: !diagramLayerSettings[trackNumber.id]?.selected,
                },
            },
        });
    };

    const onTrackNumberColorSelection = (
        trackNumberId: LayoutTrackNumberId,
        color: TrackNumberColorKey,
    ) => {
        onMapLayerMenuItemChange({ name: 'track-number-diagram', selected: true });

        onMapLayerSettingChange({
            name: 'track-number-diagram-layer',
            settings: {
                ...diagramLayerSettings,
                [trackNumberId]: {
                    ...diagramLayerSettings[trackNumberId],
                    color,
                },
            },
        });
    };

    const onToggleSwitchSelection = (layoutSwitchId: LayoutSwitchId) => {
        onSelect({
            ...createEmptyItemCollections(),
            switches: [layoutSwitchId],
            isToggle: true,
        });
    };

    const onToggleReferenceLineSelection = (trackNumberId: LayoutTrackNumberId) => {
        onSelect({
            ...createEmptyItemCollections(),
            trackNumbers: [trackNumberId],
            isToggle: true,
        });
    };

    const onToggleLocationTrackSelection = (locationTrackId: LocationTrackId) => {
        onSelect({
            ...createEmptyItemCollections(),
            locationTracks: [locationTrackId],
            isToggle: true,
        });
    };

    const onToggleOperationalPointSelection = (operationalPointId: OperationalPointId) => {
        onSelect({
            ...createEmptyItemCollections(),
            operationalPoints: [operationalPointId],
            isToggle: true,
        });
    };

    const visibleTrackNumberIds = [
        ...new Set([
            ...referenceLines.map((rl) => rl.trackNumberId),
            ...locationTracks.map((lt) => lt.trackNumberId),
            ...kmPosts.map((p) => p.trackNumberId),
        ]),
    ].sort();

    React.useEffect(() => {
        getTrackNumbers(layoutContext, changeTimes.layoutTrackNumber)
            .then((tns) =>
                tns.filter((tn) => {
                    return (
                        visibleTrackNumberIds.includes(tn.id) ||
                        selectedTrackNumberIds.some((s) => s === tn.id)
                    );
                }),
            )
            .then((visible) => setVisibleTrackNumbers(visible));
    }, [changeTimes.layoutTrackNumber, visibleTrackNumberIds.join()]);

    const filterByTrackNumberId = (tn: LayoutTrackNumberId) =>
        selectedTrackNumberIds.length === 0 || selectedTrackNumberIds.some((s) => s === tn);

    const filteredLocationTracks = locationTracks.filter((a) =>
        filterByTrackNumberId(a.trackNumberId),
    );

    const filteredReferenceLines = referenceLines.filter((l) =>
        filterByTrackNumberId(l.trackNumberId),
    );

    const filteredKmPosts = kmPosts.filter((km) => filterByTrackNumberId(km.trackNumberId));

    return (
        <div className={styles['selection-panel']}>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {`${t('selection-panel.track-numbers-title')} (${visibleTrackNumbers.length})`}
                    {!splittingState && (
                        <Eye
                            onVisibilityToggle={() => {
                                if (diagramLayerMenuItem) {
                                    onMapLayerMenuItemChange({
                                        name: 'track-number-diagram',
                                        selected: !diagramLayerMenuItem.selected,
                                    });
                                }
                            }}
                            visibility={diagramLayerMenuItem?.selected ?? false}
                        />
                    )}
                </h3>
                <div className={styles['selection-panel__content']}>
                    <TrackNumberPanel
                        settings={diagramLayerSettings}
                        trackNumbers={visibleTrackNumbers}
                        selectedTrackNumbers={selectedTrackNumberIds}
                        onSelectTrackNumber={onTrackNumberSelection}
                        onSelectColor={onTrackNumberColorSelection}
                        disabled={!!splittingState}
                    />
                </div>
            </section>
            <PrivilegeRequired privilege={VIEW_GEOMETRY}>
                <SelectionPanelGeometrySection
                    layoutContext={layoutContext}
                    changeTimes={changeTimes}
                    selectedItems={selectedItems}
                    visiblePlans={visiblePlans}
                    viewport={viewport}
                    onToggleAlignmentVisibility={onToggleAlignmentVisibility}
                    onToggleKmPostVisibility={onToggleKmPostVisibility}
                    onTogglePlanVisibility={onTogglePlanVisibility}
                    onToggleSwitchVisibility={onToggleSwitchVisibility}
                    openPlans={openPlans}
                    togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                    togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                    togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                    selectedTrackNumberIds={selectedTrackNumberIds}
                    togglePlanOpen={togglePlanOpen}
                    onSelect={onSelect}
                    disabled={!!splittingState}
                    grouping={grouping}
                    visibleSources={visibleSources}
                    planDownloadPopupOpen={planDownloadPopupOpen}
                    togglePlanDownloadPopupOpen={togglePlanDownloadPopupOpen}
                />
            </PrivilegeRequired>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {t('selection-panel.km-posts-title')} ({filteredKmPosts.length}/{kmPosts.length}
                    )
                </h3>
                <div className={styles['selection-panel__content']}>
                    <KmPostsPanel
                        kmPosts={filteredKmPosts}
                        layoutContext={layoutContext}
                        selectedKmPosts={selectedItems.kmPosts}
                        onToggleKmPostSelection={(kmPost) =>
                            onSelect({
                                ...createEmptyItemCollections(),
                                kmPosts: [kmPost.id],
                                isToggle: true,
                            })
                        }
                        disabled={!!splittingState}
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {t('selection-panel.reference-lines-title')} ({filteredReferenceLines.length}/
                    {referenceLines.length})
                </h3>
                <div className={styles['selection-panel__content']}>
                    <ReferenceLinesPanel
                        layoutContext={layoutContext}
                        referenceLines={filteredReferenceLines}
                        trackNumberChangeTime={changeTimes.layoutTrackNumber}
                        selectedTrackNumbers={selectedItems.trackNumbers}
                        canSelectReferenceLine={selectableItemTypes.includes('trackNumbers')}
                        onToggleReferenceLineSelection={onToggleReferenceLineSelection}
                        disabled={!!splittingState}
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {t('selection-panel.location-tracks-title')} ({filteredLocationTracks.length}/
                    {locationTracks.length})
                </h3>
                <div className={styles['selection-panel__content']}>
                    <LocationTracksPanel
                        locationTracks={filteredLocationTracks}
                        selectedLocationTracks={selectedItems.locationTracks}
                        canSelectLocationTrack={selectableItemTypes.includes('locationTracks')}
                        onToggleLocationTrackSelection={onToggleLocationTrackSelection}
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    <span className={styles['selection-panel__title-text']}>
                        {t('selection-panel.switches-title')} ({switchCount})
                    </span>
                    {switchCount > 0 && (
                        <PrivilegeRequired privilege={EDIT_LAYOUT}>
                            <Button
                                ref={switchMenuRef}
                                variant={ButtonVariant.GHOST}
                                size={ButtonSize.SMALL}
                                icon={Icons.More}
                                onClick={() => setShowSwitchMenu(!showSwitchMenu)}
                            />
                            {showSwitchMenu && (
                                <Menu
                                    anchorElementRef={
                                        switchMenuRef as React.MutableRefObject<HTMLElement | null>
                                    }
                                    items={switchMenuOptions}
                                    onClickOutside={() => setShowSwitchMenu(false)}
                                    onClose={() => setShowSwitchMenu(false)}
                                />
                            )}
                        </PrivilegeRequired>
                    )}
                </h3>
                <div className={styles['selection-panel__content']}>
                    <SwitchPanel
                        switches={switches}
                        switchCount={switchCount}
                        selectedSwitches={selectedItems.switches}
                        onToggleSwitchSelection={onToggleSwitchSelection}
                    />
                    <FixSwitchNamesDialog
                        isOpen={fixNamesDialogOpen}
                        onClose={handleCloseFixNamesDialog}
                        previews={fixNamesPreviews}
                        layoutBranch={layoutContext.branch as LayoutBranch}
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {t('selection-panel.operational-points-title')} ({operationalPoints.length})
                </h3>
                <div className={styles['selection-panel__content']}>
                    <OperationalPointPanel
                        operationalPoints={operationalPoints}
                        selectedOperationalPoints={selectedItems.operationalPoints}
                        onToggleOperationalPointSelection={(op) =>
                            onToggleOperationalPointSelection(op.id)
                        }
                        disabled={!!splittingState}
                    />
                </div>
            </section>
        </div>
    );
};

export default React.memo(SelectionPanel);
