import * as React from 'react';
import styles from './selection-panel.scss';
import {
    GeometryPlanLayout,
    LayoutKmPost,
    LayoutLocationTrack,
    LayoutReferenceLine,
    LayoutSwitch,
    LayoutTrackNumber,
    LayoutTrackNumberId,
} from 'track-layout/track-layout-model';
import {
    OnSelectOptions,
    OpenedPlanLayout,
    OptionalItemCollections,
    SelectableItemType,
} from 'selection/selection-model';
import { KmPostsPanel } from 'selection-panel/km-posts-panel/km-posts-panel';
import SwitchPanel from 'selection-panel/switch-panel/switch-panel';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import TrackNumberPanel from 'selection-panel/track-number-panel/track-number-panel';
import { MapViewport } from 'map/map-model';
import {
    createEmptyItemCollections,
    ToggleAccordionOpenPayload,
    ToggleAlignmentPayload,
    ToggleKmPostPayload,
    TogglePlanWithSubItemsOpenPayload,
    ToggleSwitchPayload,
} from 'selection/selection-store';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { PublishType } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { LocationTracksPanel } from 'selection-panel/location-track-panel/location-tracks-panel';
import ReferenceLinesPanel from 'selection-panel/reference-line-panel/reference-lines-panel';
import SelectionPanelGeometrySection from './selection-panel-geometry-section';

type SelectionPanelProps = {
    changeTimes: ChangeTimes;
    publishType: PublishType;
    selectedItems?: OptionalItemCollections;
    selectedPlanLayouts?: GeometryPlanLayout[];
    kmPosts: LayoutKmPost[];
    referenceLines: LayoutReferenceLine[];
    locationTracks: LayoutLocationTrack[];
    switches: LayoutSwitch[];
    viewport: MapViewport;
    onSelect: (options: OnSelectOptions) => void;
    selectableItemTypes: SelectableItemType[];
    onTogglePlanVisibility: (payload: GeometryPlanLayout | null) => void;
    onToggleAlignmentVisibility: (payload: ToggleAlignmentPayload) => void;
    onToggleSwitchVisibility: (payload: ToggleSwitchPayload) => void;
    onToggleKmPostVisibility: (payload: ToggleKmPostPayload) => void;
    togglePlanOpen: (payload: TogglePlanWithSubItemsOpenPayload) => void;
    openedPlanLayouts: OpenedPlanLayout[];
    togglePlanKmPostsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanAlignmentsOpen: (payload: ToggleAccordionOpenPayload) => void;
    togglePlanSwitchesOpen: (payload: ToggleAccordionOpenPayload) => void;
};

const SelectionPanel: React.FC<SelectionPanelProps> = ({
    publishType,
    changeTimes,
    selectedItems,
    selectedPlanLayouts,
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
    openedPlanLayouts,
    togglePlanKmPostsOpen,
    togglePlanAlignmentsOpen,
    togglePlanSwitchesOpen,
}: SelectionPanelProps) => {
    const { t } = useTranslation();
    const [visibleTrackNumbers, setVisibleTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    const [trackNumberFilter, setTrackNumberFilter] = React.useState<LayoutTrackNumber[]>([]);

    const toggleTrackNumberFilter = React.useCallback(
        (tn: LayoutTrackNumber) => {
            if (trackNumberFilter.includes(tn)) {
                setTrackNumberFilter([]);
            } else {
                setTrackNumberFilter([tn]);
            }
        },
        [trackNumberFilter],
    );

    const onToggleSwitchSelection = React.useCallback(
        (layoutSwitch: LayoutSwitch) =>
            onSelect({
                ...createEmptyItemCollections(),
                switches: [layoutSwitch.id],
                isToggle: true,
            }),
        [],
    );

    const onToggleReferenceLineSelection = React.useCallback(
        (trackNumber: string) =>
            onSelect({
                ...createEmptyItemCollections(),
                trackNumbers: [trackNumber],
                isToggle: true,
            }),
        [],
    );

    const onToggleLocationTrackSelection = React.useCallback(
        (locationTrack: string) =>
            onSelect({
                ...createEmptyItemCollections(),
                locationTracks: [locationTrack],
                isToggle: true,
            }),
        [],
    );

    const visibleTrackNumberIds = [
        ...new Set([
            ...referenceLines.map((rl) => rl.trackNumberId),
            ...locationTracks.map((lt) => lt.trackNumberId),
            ...kmPosts.map((p) => p.trackNumberId),
        ]),
    ].sort();
    React.useEffect(() => {
        getTrackNumbers(publishType, changeTimes.layoutTrackNumber)
            .then((tns) =>
                tns.filter((tn) => {
                    return (
                        visibleTrackNumberIds.includes(tn.id) ||
                        trackNumberFilter.some((f) => f.id == tn.id)
                    );
                }),
            )
            .then((visible) => setVisibleTrackNumbers(visible));
    }, [changeTimes.layoutTrackNumber, JSON.stringify(visibleTrackNumberIds)]);

    const filterByTrackNumberId = (tn: LayoutTrackNumberId) =>
        trackNumberFilter.length == 0 || trackNumberFilter.some((f) => f.id === tn);

    const filteredLocationTracks = locationTracks.filter((a) =>
        filterByTrackNumberId(a.trackNumberId),
    );
    const filteredReferenceLines = React.useMemo(() => {
        return referenceLines.filter((l) => filterByTrackNumberId(l.trackNumberId));
    }, [referenceLines, trackNumberFilter]);

    const filteredKmPosts = kmPosts.filter((km) => filterByTrackNumberId(km.trackNumberId));
    return (
        <div className={styles['selection-panel']}>
            <section>
                <h3 className={styles['selection-panel__title']}>{`${t(
                    'selection-panel.track-numbers-title',
                )} (${visibleTrackNumbers.length})`}</h3>
                <div className={styles['selection-panel__content']}>
                    <TrackNumberPanel
                        trackNumbers={visibleTrackNumbers}
                        selectedTrackNumbers={trackNumberFilter}
                        onSelectTrackNumber={toggleTrackNumberFilter}
                    />
                </div>
            </section>
            <SelectionPanelGeometrySection
                publishType={publishType}
                changeTimes={changeTimes}
                selectedItems={selectedItems}
                selectedPlanLayouts={selectedPlanLayouts}
                viewport={viewport}
                onToggleAlignmentVisibility={onToggleAlignmentVisibility}
                onToggleKmPostVisibility={onToggleKmPostVisibility}
                onTogglePlanVisibility={onTogglePlanVisibility}
                onToggleSwitchVisibility={onToggleSwitchVisibility}
                openedPlanLayouts={openedPlanLayouts}
                togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                trackNumberFilter={trackNumberFilter}
                togglePlanOpen={togglePlanOpen}
                onSelect={onSelect}
            />
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {t('selection-panel.km-posts-title')} ({filteredKmPosts.length}/{kmPosts.length}
                    )
                </h3>
                <div className={styles['selection-panel__content']}>
                    <KmPostsPanel
                        kmPosts={filteredKmPosts}
                        selectedKmPosts={selectedItems?.kmPosts}
                        onToggleKmPostSelection={(kmPost) =>
                            onSelect({
                                ...createEmptyItemCollections(),
                                kmPosts: [kmPost.id],
                                isToggle: true,
                            })
                        }
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
                        publishType={publishType}
                        referenceLines={filteredReferenceLines}
                        trackNumberChangeTime={changeTimes.layoutTrackNumber}
                        selectedTrackNumbers={selectedItems?.trackNumbers}
                        canSelectReferenceLine={selectableItemTypes.includes('trackNumbers')}
                        onToggleReferenceLineSelection={onToggleReferenceLineSelection}
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
                        selectedLocationTracks={selectedItems?.locationTracks}
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
                        selectedSwitches={selectedItems?.switches}
                        onToggleSwitchSelection={onToggleSwitchSelection}
                    />
                </div>
            </section>
        </div>
    );
};

export default React.memo(SelectionPanel);
