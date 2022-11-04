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
import { GeometryPlanPanel } from 'selection-panel/geometry-plan-panel/geometry-plan-panel';
import {
    OnSelectOptions,
    OpenedPlanLayout,
    OptionalItemCollections,
    SelectableItemType,
} from 'selection/selection-model';
import { GeometryPlanHeader, SortByValue, SortOrderValue } from 'geometry/geometry-model';
import { createClassName } from 'vayla-design-lib/utils';
import { KmPostsPanel } from 'selection-panel/km-posts-panel/km-posts-panel';
import SwitchPanel from 'selection-panel/switch-panel/switch-panel';
import { getTrackNumbers } from 'track-layout/track-layout-api';
import TrackNumberPanel from 'selection-panel/track-number-panel/track-number-panel';
import { getGeometryPlanHeaders } from 'geometry/geometry-api';
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
import { LocationTracksPanel } from 'selection-panel/alignment-panel/location-tracks-panel';
import ReferenceLinesPanel from 'selection-panel/alignment-panel/reference-lines-panel';

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

const MAX_PLAN_HEADERS = 20;

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
    const {t} = useTranslation();
    const [visibleTrackNumbers, setVisibleTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    const [trackNumberFilter, setTrackNumberFilter] = React.useState<LayoutTrackNumber[]>([]);
    const [planHeaders, setPlanHeaders] = React.useState<GeometryPlanHeader[]>([]);
    const [planHeaderCount, setPlanHeaderCount] = React.useState<number>(0);

    React.useEffect(() => {
        getGeometryPlanHeaders(
            MAX_PLAN_HEADERS,
            0,
            viewport.area,
            ['GEOVIITE', 'GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'],
            trackNumberFilter.map((tn) => tn.id),
            undefined,
            SortByValue.UPLOADED_AT,
            SortOrderValue.ASCENDING,
        ).then((page) => {
            setPlanHeaders(page.items);
            setPlanHeaderCount(page.totalCount);
        });
    }, [viewport.area, changeTimes.geometryPlan, trackNumberFilter]);

    const toggleTrackNumberFilter = React.useCallback((tn: LayoutTrackNumber) => {
            if (trackNumberFilter.includes(tn)) {
                setTrackNumberFilter([]);
            } else {
                setTrackNumberFilter([tn]);
            }
        }
        , []);

    const onToggleSwitchSelection = React.useCallback((layoutSwitch: LayoutSwitch) =>
            onSelect({
                ...createEmptyItemCollections(),
                switches: [layoutSwitch],
                isToggle: true,
            })
        , []);

    const onToggleReferenceLineSelection = React.useCallback(
        (trackNumber: string, referenceLine: string) =>
            onSelect({
                ...createEmptyItemCollections(),
                trackNumbers: [trackNumber],
                referenceLines: [referenceLine],
                isToggle: true,
            })
        , []);

    const onToggleLocationTrackSelection = React.useCallback((locationTrack: string) =>
            onSelect({
                ...createEmptyItemCollections(),
                locationTracks: [locationTrack],
                isToggle: true,
            })
        , []);

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
        return referenceLines.filter((l) =>
            filterByTrackNumberId(l.trackNumberId),
        );
    }, [referenceLines]);

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
            {planHeaders && (
                <section>
                    <h3 className={styles['selection-panel__title']}>
                        {`${t('selection-panel.geometries-title')} (${
                            planHeaders.length
                        }/${planHeaderCount})`}
                    </h3>
                    <div
                        className={createClassName(
                            styles['selection-panel__content'],
                            styles['selection-panel__content--unpadded'],
                        )}>
                        {planHeaders.length == planHeaderCount &&
                            planHeaders.map((h) => {
                                return (
                                    <GeometryPlanPanel
                                        key={h.id}
                                        planHeader={h}
                                        onPlanHeaderSelection={(header) =>
                                            onSelect({
                                                ...createEmptyItemCollections(),
                                                geometryPlans: [header],
                                                isToggle: true,
                                            })
                                        }
                                        publishType={publishType}
                                        changeTimes={changeTimes}
                                        onTogglePlanVisibility={onTogglePlanVisibility}
                                        onToggleAlignmentVisibility={onToggleAlignmentVisibility}
                                        onToggleAlignmentSelection={(alignment) =>
                                            onSelect({
                                                ...createEmptyItemCollections(),
                                                geometryAlignments: [
                                                    {
                                                        geometryItem: alignment,
                                                        planId: h.id,
                                                    },
                                                ],
                                                isToggle: true,
                                            })
                                        }
                                        onToggleSwitchVisibility={onToggleSwitchVisibility}
                                        onToggleSwitchSelection={(switchItem) =>
                                            onSelect({
                                                ...createEmptyItemCollections(),
                                                geometrySwitches: [
                                                    {
                                                        geometryItem: switchItem,
                                                        planId: h.id,
                                                    },
                                                ],
                                                isToggle: true,
                                            })
                                        }
                                        onToggleKmPostVisibility={onToggleKmPostVisibility}
                                        onToggleKmPostSelection={(kmPost) =>
                                            onSelect({
                                                ...createEmptyItemCollections(),
                                                geometryKmPosts: [
                                                    {
                                                        geometryItem: kmPost,
                                                        planId: h.id,
                                                    },
                                                ],
                                                isToggle: true,
                                            })
                                        }
                                        selectedItems={selectedItems}
                                        selectedPlanLayouts={selectedPlanLayouts}
                                        togglePlanOpen={togglePlanOpen}
                                        openedPlanLayouts={openedPlanLayouts}
                                        togglePlanKmPostsOpen={togglePlanKmPostsOpen}
                                        togglePlanAlignmentsOpen={togglePlanAlignmentsOpen}
                                        togglePlanSwitchesOpen={togglePlanSwitchesOpen}
                                    />
                                );
                            })}
                        {planHeaders.length < planHeaderCount && (
                            <span className={styles['selection-panel__subtitle']}>{`${t(
                                'selection-panel.zoom-closer',
                            )}`}</span>
                        )}

                        {planHeaders.length === 0 && (
                            <span className={styles['selection-panel__subtitle']}>
                                {`${t('selection-panel.no-results')}`}{' '}
                            </span>
                        )}
                    </div>
                </section>
            )}
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {`${t('selection-panel.km-posts-title')}  (${filteredKmPosts.length})`}
                </h3>
                <div className={styles['selection-panel__content']}>
                    <KmPostsPanel
                        kmPosts={filteredKmPosts}
                        selectedKmPosts={selectedItems?.kmPosts}
                        onToggleKmPostSelection={(kmPost) =>
                            onSelect({
                                ...createEmptyItemCollections(),
                                kmPosts: [kmPost],
                                isToggle: true,
                            })
                        }
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {`${t('selection-panel.reference-lines-title')}  (${
                        filteredReferenceLines.length
                    })`}
                </h3>
                <div className={styles['selection-panel__content']}>
                    <ReferenceLinesPanel
                        publishType={publishType}
                        referenceLines={filteredReferenceLines}
                        trackNumberChangeTime={changeTimes.layoutTrackNumber}
                        selectedReferenceLines={selectedItems?.referenceLines}
                        canSelectReferenceLine={selectableItemTypes.includes('referenceLines')}
                        onToggleReferenceLineSelection={onToggleReferenceLineSelection}
                    />
                </div>
            </section>
            <section>
                <h3 className={styles['selection-panel__title']}>
                    {`${t('selection-panel.location-tracks-title')}  (${locationTracks.length})`}
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
                    {`${t('selection-panel.switches-title')}  (${switches.length})`}
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
