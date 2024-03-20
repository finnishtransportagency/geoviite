import * as React from 'react';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import {
    EditState,
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { LinkingState, LinkingType, SuggestedSwitch } from 'linking/linking-model';
import { SelectedGeometryItem } from 'selection/selection-model';
import GeometryAlignmentLinkingContainer from 'tool-panel/geometry-alignment/geometry-alignment-linking-container';
import { filterNotEmpty, filterUnique, first } from 'utils/array-utils';
import LocationTrackInfoboxLinkingContainer from 'tool-panel/location-track/location-track-infobox-linking-container';
import { getKmPosts } from 'track-layout/layout-km-post-api';
import TrackNumberInfoboxLinkingContainer from 'tool-panel/track-number/track-number-infobox-linking-container';
import { useLoader } from 'utils/react-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { MapViewport } from 'map/map-model';
import { getGeometryPlanHeaders, getTrackLayoutPlansByIds } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import {
    GeometryKmPostInfoboxVisibilities,
    InfoboxVisibilities,
    KmPostInfoboxVisibilities,
} from 'track-layout/track-layout-slice';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { KmPostInfoboxContainer } from 'tool-panel/km-post/km-post-infobox-container';
import { GeometryKmPostInfoboxContainer } from 'tool-panel/km-post/geometry-km-post-infobox-container';
import { SwitchInfoboxContainer } from 'tool-panel/switch/switch-infobox-container';
import { SuggestedSwitchInfoboxContainer } from 'tool-panel/switch/dialog/suggested-switch-infobox-container';
import { GeometrySwitchInfoboxContainer } from 'tool-panel/switch/dialog/geometry-switch-infobox-container';
import { LocationTrackTaskListContainer } from 'tool-panel/location-track/location-track-task-list/location-track-task-list-container';
import { TabHeader } from 'geoviite-design-lib/tab-header/tab-header';
import { LayoutContext } from 'common/common-model';

type ToolPanelProps = {
    planIds: GeometryPlanId[];
    trackNumberIds: LayoutTrackNumberId[];
    kmPostIds: LayoutKmPostId[];
    geometryKmPostIds: SelectedGeometryItem<GeometryKmPostId>[];
    switchIds: LayoutSwitchId[];
    geometrySwitchIds: SelectedGeometryItem<GeometrySwitchId>[];
    locationTrackIds: LocationTrackId[];
    geometryAlignmentIds: SelectedGeometryItem<GeometryAlignmentId>[];
    suggestedSwitches: SuggestedSwitch[];
    linkingState?: LinkingState;
    splittingState?: SplittingState;
    changeTimes: ChangeTimes;
    layoutContext: LayoutContext;
    onDataChange: () => void;
    selectedAsset: ToolPanelAsset | undefined;
    setSelectedAsset: (id: ToolPanelAsset | undefined) => void;
    viewport: MapViewport;
    infoboxVisibilities: InfoboxVisibilities;
    onInfoboxVisibilityChange: (visibilities: InfoboxVisibilities) => void;
    verticalGeometryDiagramVisible: boolean;
    onHoverOverPlanSection: (item: HighlightedAlignment | undefined) => void;
};

export type ToolPanelAsset = {
    id: string;
    type:
        | 'LOCATION_TRACK'
        | 'SWITCH'
        | 'KM_POST'
        | 'REFERENCE_LINE'
        | 'TRACK_NUMBER'
        | 'GEOMETRY_ALIGNMENT'
        | 'GEOMETRY_PLAN'
        | 'GEOMETRY_KM_POST'
        | 'GEOMETRY_SWITCH';
};

type ToolPanelTab = {
    asset: ToolPanelAsset;
    title: string;
    element: React.ReactElement;
};

const isSameAsset = (a: ToolPanelAsset | undefined, b: ToolPanelAsset | undefined) =>
    !!a && !!b && a.id === b.id && a.type === b.type;

const ToolPanel: React.FC<ToolPanelProps> = ({
    planIds,
    trackNumberIds,
    kmPostIds,
    geometryKmPostIds,
    switchIds,
    geometrySwitchIds,
    locationTrackIds,
    geometryAlignmentIds,
    suggestedSwitches,
    linkingState,
    splittingState,
    changeTimes,
    layoutContext,
    onDataChange,
    selectedAsset,
    setSelectedAsset,
    viewport,
    infoboxVisibilities,
    onInfoboxVisibilityChange,
    verticalGeometryDiagramVisible,
    onHoverOverPlanSection,
}: ToolPanelProps) => {
    const [previousTabs, setPreviousTabs] = React.useState<ToolPanelTab[]>([]);
    const [tabs, setTabs] = React.useState<ToolPanelTab[]>([]);

    const tracksSwitchesKmPostsPlans = useLoader(() => {
        const trackNumbersPromise = getTrackNumbers(
            layoutContext,
            changeTimes.layoutTrackNumber,
            true,
        );
        const locationTracksPromise = getLocationTracks(locationTrackIds, layoutContext);
        const switchesPromise = getSwitches(switchIds, layoutContext);
        const kmPostsPromise = getKmPosts(kmPostIds, layoutContext);
        const plansPromise = getGeometryPlanHeaders(planIds);
        const elementPlanIds = [
            ...geometryKmPostIds.map((kmp) => kmp.planId),
            ...geometrySwitchIds.map((s) => s.planId),
            ...geometryAlignmentIds.map((s) => s.planId),
        ].filter(filterUnique);
        const elementPlansPromise = getTrackLayoutPlansByIds(
            elementPlanIds,
            changeTimes.geometryPlan,
            false,
        );

        return Promise.all([
            // TODO: GVT-2014 Check the nullability in these api-calls/caches
            // It is possible for an item in the id-list to not exist, but these functions do not typically return null in that case.
            // The whole thing needs a check-up. What's the correct handling?
            // Don't double-check nulls and make sure that types match what is returned.
            locationTracksPromise,
            switchesPromise.then((l) => l.filter(filterNotEmpty)),
            kmPostsPromise.then((l) => l.filter(filterNotEmpty)),
            trackNumbersPromise.then((l) => l.filter(filterNotEmpty)),
            plansPromise.then((l) => l.filter(filterNotEmpty)),
            elementPlansPromise,
        ]);
    }, [
        locationTrackIds,
        changeTimes.layoutLocationTrack,
        layoutContext,
        switchIds,
        changeTimes.layoutSwitch,
        kmPostIds,
        changeTimes.layoutKmPost,
        changeTimes.layoutTrackNumber,
        changeTimes.split,
        planIds,
        geometryKmPostIds,
        geometrySwitchIds,
        geometryAlignmentIds,
    ]);

    const locationTracks = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[0]) || [];
    const switches = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[1]) || [];
    const kmPosts = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[2]) || [];
    const trackNumbers = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[3]) || [];
    const planHeaders = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[4]) || [];

    const getPlan = (id: GeometryPlanId) =>
        tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[5].find((p) => p.id === id);

    const infoboxVisibilityChange = (
        key: keyof InfoboxVisibilities,
        visibilities: InfoboxVisibilities[keyof InfoboxVisibilities],
    ) => {
        onInfoboxVisibilityChange({
            ...infoboxVisibilities,
            [key]: visibilities,
        });
    };

    // Draft-only entities should be hidden when viewing in official mode. Show everything in draft mode
    const visibleByTypeAndPublishType = ({ editState }: { editState: EditState }) =>
        layoutContext.publicationState === 'DRAFT' || editState !== 'CREATED';

    React.useEffect(() => {
        if (tracksSwitchesKmPostsPlans === undefined) {
            return;
        }
        const planTabs = planHeaders.map((p: GeometryPlanHeader) => {
            return {
                asset: { type: 'GEOMETRY_PLAN', id: p.id },
                title: p.fileName,
                element: (
                    <GeometryPlanInfobox
                        planHeader={p}
                        visibilities={infoboxVisibilities.geometryPlan}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometryPlan', visibilities)
                        }
                    />
                ),
            } as ToolPanelTab;
        });

        const trackNumberTabs = trackNumberIds
            .map((tnId) => trackNumbers?.find((tn) => tn.id === tnId))
            .filter(filterNotEmpty)
            .filter(visibleByTypeAndPublishType)
            .map((t) => {
                return {
                    asset: { type: 'TRACK_NUMBER', id: t.id },
                    title: t.number,
                    element: (
                        <TrackNumberInfoboxLinkingContainer
                            trackNumber={t}
                            visibilities={infoboxVisibilities.trackNumber}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('trackNumber', visibilities)
                            }
                            setHoveredOverItem={onHoverOverPlanSection}
                        />
                    ),
                } as ToolPanelTab;
            });

        const layoutKmPostTabs = kmPosts.filter(visibleByTypeAndPublishType).map((k) => {
            return {
                asset: { type: 'KM_POST', id: k.id },
                title: k.kmNumber,
                element: (
                    <KmPostInfoboxContainer
                        visibilities={infoboxVisibilities.kmPost}
                        onVisibilityChange={(visibilities: KmPostInfoboxVisibilities) =>
                            infoboxVisibilityChange('kmPost', visibilities)
                        }
                        onDataChange={onDataChange}
                        kmPost={k}
                    />
                ),
            } as ToolPanelTab;
        });

        const geometryKmPostTabs = geometryKmPostIds.map(
            (k: SelectedGeometryItem<LayoutKmPostId>) => {
                const kmPost = getPlan(k.planId)?.kmPosts?.find((p) => p.sourceId === k.geometryId);
                return {
                    asset: { type: 'GEOMETRY_KM_POST', id: k.geometryId },
                    title: kmPost?.kmNumber ?? '...',
                    element: kmPost ? (
                        <GeometryKmPostInfoboxContainer
                            kmPost={kmPost}
                            planId={k.planId}
                            visibility={infoboxVisibilities.geometryKmPost}
                            onVisiblityChange={(visibilities: GeometryKmPostInfoboxVisibilities) =>
                                infoboxVisibilityChange('geometryKmPost', visibilities)
                            }
                        />
                    ) : (
                        <Spinner />
                    ),
                } as ToolPanelTab;
            },
        );

        const switchTabs = switches.filter(visibleByTypeAndPublishType).map((s) => {
            return {
                asset: { type: 'SWITCH', id: s.id },
                title: s.name,
                element: (
                    <SwitchInfoboxContainer
                        visibilities={infoboxVisibilities.switch}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('switch', visibilities)
                        }
                        switchId={s.id}
                        onDataChange={onDataChange}
                    />
                ),
            } as ToolPanelTab;
        });

        const suggestedSwitchTabs: ToolPanelTab[] = suggestedSwitches.map((ss) => {
            return {
                asset: { type: 'GEOMETRY_SWITCH', id: ss.id },
                title: ss.name ?? '...',
                element: (
                    <SuggestedSwitchInfoboxContainer
                        visibilities={infoboxVisibilities.geometrySwitch}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometrySwitch', visibilities)
                        }
                        layoutSwitch={first(switches)}
                        suggestedSwitch={ss}
                    />
                ),
            };
        });
        const geometrySwitchTabs: ToolPanelTab[] = geometrySwitchIds
            .filter((s) => !suggestedSwitches.some((ss) => ss.geometrySwitchId === s.geometryId))
            .map((s) => {
                const geomSwitch = getPlan(s.planId)?.switches?.find(
                    (gs) => gs.sourceId === s.geometryId,
                );
                return {
                    asset: { type: 'GEOMETRY_SWITCH', id: s.geometryId },
                    title: geomSwitch?.name ?? '...',
                    element: (
                        <GeometrySwitchInfoboxContainer
                            visibilities={infoboxVisibilities.geometrySwitch}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('geometrySwitch', visibilities)
                            }
                            switchId={s.geometryId}
                            layoutSwitch={first(switches)}
                            planId={s.planId ?? undefined}
                        />
                    ),
                };
            });

        const locationTrackTabs = locationTracks
            .filter(visibleByTypeAndPublishType)
            .map((track) => {
                return {
                    asset: { type: 'LOCATION_TRACK', id: track.id },
                    title: track.name,
                    element: (
                        <LocationTrackInfoboxLinkingContainer
                            visibilities={infoboxVisibilities.locationTrack}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('locationTrack', visibilities)
                            }
                            locationTrackId={track.id}
                            onDataChange={onDataChange}
                            onHoverOverPlanSection={onHoverOverPlanSection}
                        />
                    ),
                } as ToolPanelTab;
            });

        const geometryAlignmentTabs = geometryAlignmentIds.map((aId) => {
            const header = getPlan(aId.planId)?.alignments?.find(
                (a) => a.header.id === aId.geometryId,
            )?.header;
            return {
                asset: { type: 'GEOMETRY_ALIGNMENT', id: aId.geometryId },
                title: header?.name ?? '...',
                element: header ? (
                    <GeometryAlignmentLinkingContainer
                        visibilities={infoboxVisibilities.geometryAlignment}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometryAlignment', visibilities)
                        }
                        geometryAlignment={header}
                        selectedLocationTrackId={first(locationTrackIds)}
                        selectedTrackNumberId={first(trackNumberIds)}
                        planId={aId.planId}
                    />
                ) : (
                    <Spinner />
                ),
            } as ToolPanelTab;
        });

        const allTabs = [
            ...geometryKmPostTabs,
            ...layoutKmPostTabs,
            ...suggestedSwitchTabs,
            ...geometrySwitchTabs,
            ...switchTabs,
            ...geometryAlignmentTabs,
            ...locationTrackTabs,
            ...trackNumberTabs,
            ...planTabs,
        ];
        setTabs(allTabs);
    }, [
        planHeaders,
        trackNumbers,
        trackNumberIds,
        kmPosts,
        geometryKmPostIds,
        switches,
        geometrySwitchIds,
        suggestedSwitches,
        locationTracks,
        geometryAlignmentIds,
        linkingState,
        splittingState,
        layoutContext,
        viewport,
        changeTimes,
        infoboxVisibilities,
        verticalGeometryDiagramVisible,
    ]);

    React.useEffect(() => {
        const newTabs = tabs.filter(
            (t) => !previousTabs.some((pt) => isSameAsset(t.asset, pt.asset)),
        );

        const firstTab = first(newTabs);
        if (firstTab) {
            if (selectedAsset && newTabs.some((nt) => isSameAsset(nt.asset, selectedAsset))) {
                changeTab(selectedAsset);
            } else {
                changeTab(firstTab.asset);
            }
        }

        if (!tabs.length) {
            setSelectedAsset(undefined);
        }
        setPreviousTabs(tabs);
    }, [tabs]);

    function changeTab(tab: ToolPanelAsset) {
        let lockToAsset;

        if (linkingState?.type === LinkingType.LinkingAlignment) {
            lockToAsset = tabs.find(
                (t) =>
                    t.asset.type === 'LOCATION_TRACK' &&
                    t.asset.id === linkingState.layoutAlignmentId,
            )?.asset;
        } else if (
            linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
            linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
            linkingState?.type === LinkingType.UnknownAlignment
        ) {
            lockToAsset = tabs.find(
                (t) =>
                    t.asset.type === 'GEOMETRY_ALIGNMENT' &&
                    t.asset.id === linkingState.geometryAlignmentId,
            )?.asset;
        } else if (linkingState?.type === LinkingType.LinkingSwitch) {
            lockToAsset = tabs.find((t) => {
                return (
                    (t.asset.type === 'GEOMETRY_SWITCH' &&
                        t.asset.id === linkingState.suggestedSwitch.geometrySwitchId) ||
                    suggestedSwitches.some((s) => t.asset.id === s.id)
                );
            })?.asset;
        } else if (linkingState?.type === LinkingType.LinkingKmPost) {
            lockToAsset = tabs.find((t) => {
                return (
                    t.asset.type === 'GEOMETRY_KM_POST' &&
                    t.asset.id === linkingState.geometryKmPostId
                );
            })?.asset;
        }

        setSelectedAsset(lockToAsset ? lockToAsset : tab);
    }

    const anyTabSelected = tabs.some((t) => isSameAsset(t.asset, selectedAsset));
    return (
        <div className="tool-panel">
            {tabs.length > 1 && (
                <div className="tool-panel__tab-bar" qa-id="tool-panel-tabs">
                    {tabs.map((t, tabIndex) => {
                        const selected = anyTabSelected
                            ? isSameAsset(t.asset, selectedAsset)
                            : tabIndex === 0;
                        return (
                            <TabHeader
                                key={t.asset.type + '_' + t.asset.id}
                                selected={selected}
                                onClick={() => changeTab(t.asset)}>
                                {t.title}
                            </TabHeader>
                        );
                    })}
                </div>
            )}
            {anyTabSelected
                ? tabs.find((t) => isSameAsset(t.asset, selectedAsset))?.element
                : first(tabs)?.element}
            <LocationTrackTaskListContainer />
        </div>
    );
};

export default React.memo(ToolPanel);
