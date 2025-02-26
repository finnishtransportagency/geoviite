import * as React from 'react';
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
import { LayoutContext } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LocationTracksPanel } from 'selection-panel/location-track-panel/location-tracks-panel';
import ReferenceLinesPanel from 'selection-panel/reference-line-panel/reference-lines-panel';
import SelectionPanelGeometrySection from './selection-panel-geometry-section';
import { ChangeTimes } from 'common/common-slice';
import { Eye } from 'geoviite-design-lib/eye/eye';
import { TrackNumberColorKey } from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { PrivilegeRequired } from 'user/privilege-required';
import { VIEW_GEOMETRY } from 'user/user-model';
import { objectEntries } from 'utils/array-utils';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';
import { PlanSource } from 'geometry/geometry-model';

type SelectionPanelProps = {
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    selectedItems: OptionalItemCollections;
    openPlans: OpenPlanLayout[];
    visiblePlans: VisiblePlanLayout[];
    kmPosts: LayoutKmPost[];
    referenceLines: LayoutReferenceLine[];
    locationTracks: LayoutLocationTrack[];
    switches: LayoutSwitch[];
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
    switches,
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
}: SelectionPanelProps) => {
    const { t } = useTranslation();
    const [visibleTrackNumbers, setVisibleTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);

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
        onMapLayerMenuItemChange({ name: 'track-number-diagram', visible: true });

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

    console.log(!!splittingState);

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
                                        visible: !diagramLayerMenuItem.visible,
                                    });
                                }
                            }}
                            visibility={diagramLayerMenuItem?.visible ?? false}
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
                    {t('selection-panel.switches-title')} ({switches.length})
                </h3>
                <div className={styles['selection-panel__content']}>
                    <SwitchPanel
                        switches={switches}
                        selectedSwitches={selectedItems.switches}
                        onToggleSwitchSelection={onToggleSwitchSelection}
                    />
                </div>
            </section>
        </div>
    );
};

export default React.memo(SelectionPanel);
