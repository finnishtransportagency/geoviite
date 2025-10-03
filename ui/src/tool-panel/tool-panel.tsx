import * as React from 'react';
import GeometryPlanInfobox from 'tool-panel/geometry-plan/geometry-plan-infobox';
import {
    GeometryAlignmentId,
    GeometryKmPostId,
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import {
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignmentType,
    OperationalPointId,
} from 'track-layout/track-layout-model';
import { LinkingState, LinkingType } from 'linking/linking-model';
import { SelectedGeometryItem } from 'selection/selection-model';
import GeometryAlignmentLinkingContainer from 'tool-panel/geometry-alignment/geometry-alignment-linking-container';
import { compareByField, filterNotEmpty, filterUnique, first } from 'utils/array-utils';
import LocationTrackInfoboxLinkingContainer from 'tool-panel/location-track/location-track-infobox-linking-container';
import { getKmPosts } from 'track-layout/layout-km-post-api';
import TrackNumberInfoboxLinkingContainer from 'tool-panel/track-number/track-number-infobox-linking-container';
import { useLoader } from 'utils/react-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { MapViewport } from 'map/map-model';
import { getGeometryPlanHeaders, getTrackLayoutPlans } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import {
    GeometryKmPostInfoboxVisibilities,
    InfoboxVisibilities,
    KmPostInfoboxVisibilities,
    SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID,
    TOOL_PANEL_ASSET_ORDER,
} from 'track-layout/track-layout-slice';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { KmPostInfoboxContainer } from 'tool-panel/km-post/km-post-infobox-container';
import { GeometryKmPostInfoboxContainer } from 'tool-panel/km-post/geometry-km-post-infobox-container';
import { SwitchInfoboxContainer } from 'tool-panel/switch/switch-infobox-container';
import { GeometrySwitchInfoboxContainer } from 'tool-panel/switch/geometry-switch-infobox-container';
import { LocationTrackTaskListContainer } from 'tool-panel/location-track/location-track-task-list/location-track-task-list-container';
import { TabHeader, TabHeaderSize } from 'geoviite-design-lib/tab-header/tab-header';
import { LayoutContext } from 'common/common-model';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { LayoutSwitchLinkingInfoboxContainer } from 'tool-panel/switch/layout-switch-linking-infobox-container';
import { OperationalPointInfoboxContainer } from './operational-point/operational-point-infobox-container';
import { getManyOperationalPoints } from 'track-layout/layout-operating-point-api';

type ToolPanelProps = {
    planIds: GeometryPlanId[];
    trackNumberIds: LayoutTrackNumberId[];
    kmPostIds: LayoutKmPostId[];
    geometryKmPostIds: SelectedGeometryItem<GeometryKmPostId>[];
    switchIds: LayoutSwitchId[];
    geometrySwitchIds: SelectedGeometryItem<GeometrySwitchId>[];
    locationTrackIds: LocationTrackId[];
    geometryAlignmentIds: SelectedGeometryItem<GeometryAlignmentId>[];
    operationalPointIds: OperationalPointId[];
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

export type ToolPanelAssetType =
    | 'LOCATION_TRACK'
    | 'SWITCH'
    | 'KM_POST'
    | 'TRACK_NUMBER'
    | 'GEOMETRY_ALIGNMENT'
    | 'GEOMETRY_PLAN'
    | 'GEOMETRY_KM_POST'
    | 'GEOMETRY_SWITCH'
    | 'SUGGESTED_SWITCH'
    | 'OPERATING_POINT';
export type ToolPanelAsset = {
    id: string;
    type: ToolPanelAssetType;
};

type ToolPanelTab = {
    asset: ToolPanelAsset;
    title: string;
    element: React.ReactElement;
};

const isSameAsset = (a: ToolPanelAsset | undefined, b: ToolPanelAsset | undefined) =>
    !!a && !!b && a.id === b.id && a.type === b.type;

const linkingStateLayoutAlignmentTabType = (type: MapAlignmentType) => {
    switch (type) {
        case MapAlignmentType.LocationTrack:
            return 'LOCATION_TRACK';
        case MapAlignmentType.ReferenceLine:
            return 'TRACK_NUMBER';
        default:
            return exhaustiveMatchingGuard(type);
    }
};

const ToolPanel: React.FC<ToolPanelProps> = ({
    planIds,
    trackNumberIds,
    kmPostIds,
    geometryKmPostIds,
    switchIds,
    geometrySwitchIds,
    locationTrackIds,
    geometryAlignmentIds,
    operationalPointIds,
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
    const [tabs, setTabs] = React.useState<ToolPanelTab[]>([]);

    const tracksSwitchesKmPostsPlansOperationalPoints = useLoader(() => {
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
        const elementPlansPromise = getTrackLayoutPlans(
            elementPlanIds,
            changeTimes.geometryPlan,
            false,
        );
        const operationalPointsPromise = getManyOperationalPoints(
            operationalPointIds,
            layoutContext,
            changeTimes.operatingPoints,
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
            operationalPointsPromise,
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
        changeTimes.geometryPlan,
        planIds,
        geometryKmPostIds,
        geometrySwitchIds,
        geometryAlignmentIds,
    ]);

    const locationTracks =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[0]) ||
        [];
    const switches =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[1]) ||
        [];
    const kmPosts =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[2]) ||
        [];
    const trackNumbers =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[3]) ||
        [];
    const planHeaders =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[4]) ||
        [];

    const getPlan = (id: GeometryPlanId) =>
        tracksSwitchesKmPostsPlansOperationalPoints &&
        tracksSwitchesKmPostsPlansOperationalPoints[5].find((p) => p.layout?.id === id);
    const operationalPoints =
        (tracksSwitchesKmPostsPlansOperationalPoints &&
            tracksSwitchesKmPostsPlansOperationalPoints[6]) ||
        [];

    const infoboxVisibilityChange = React.useCallback(
        (
            key: keyof InfoboxVisibilities,
            visibilities: InfoboxVisibilities[keyof InfoboxVisibilities],
        ) => {
            onInfoboxVisibilityChange({
                ...infoboxVisibilities,
                [key]: visibilities,
            });
        },
        [onInfoboxVisibilityChange, infoboxVisibilities],
    );

    React.useEffect(() => {
        if (tracksSwitchesKmPostsPlansOperationalPoints === undefined) {
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
                        changeTimes={changeTimes}
                    />
                ),
            } as ToolPanelTab;
        });

        const trackNumberTabs = trackNumberIds
            .map((tnId) => trackNumbers?.find((tn) => tn.id === tnId))
            .filter(filterNotEmpty)
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

        const layoutKmPostTabs = kmPosts.map((k) => {
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
            (k: SelectedGeometryItem<GeometryKmPostId>) => {
                const kmPost = getPlan(k.planId)?.layout?.kmPosts?.find(
                    (p) => p.sourceId === k.geometryId,
                );
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

        const switchTabs = switches.map((s) => {
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

        const suggestedSwitchTabs: ToolPanelTab[] =
            linkingState?.type === LinkingType.LinkingLayoutSwitch ||
            linkingState?.type === LinkingType.LinkingGeometrySwitch
                ? [
                      {
                          asset: {
                              type: 'SUGGESTED_SWITCH',
                              id: SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID,
                          },
                          title: linkingState.suggestedSwitchName,
                          element:
                              linkingState.type === LinkingType.LinkingGeometrySwitch ? (
                                  <GeometrySwitchInfoboxContainer
                                      linkingState={linkingState}
                                      visibilities={infoboxVisibilities.geometrySwitch}
                                      onVisibilityChange={(visibilities) =>
                                          infoboxVisibilityChange('geometrySwitch', visibilities)
                                      }
                                      geometrySwitchId={linkingState.geometrySwitchId}
                                      geometryPlanId={linkingState.geometryPlanId}
                                  />
                              ) : (
                                  <LayoutSwitchLinkingInfoboxContainer
                                      layoutSwitchId={linkingState.layoutSwitchId}
                                      suggestedSwitch={linkingState.suggestedSwitch}
                                      visibilities={infoboxVisibilities.geometrySwitch}
                                      onVisibilityChange={(visibilities) =>
                                          infoboxVisibilityChange('geometrySwitch', visibilities)
                                      }
                                  />
                              ),
                      },
                  ]
                : [];

        const geometrySwitchTabs: ToolPanelTab[] = geometrySwitchIds
            .filter((s) =>
                linkingState?.type === LinkingType.LinkingGeometrySwitch
                    ? s.geometryId === linkingState.geometrySwitchId
                    : true,
            )
            .map((s) => {
                const geometrySwitchLayout = getPlan(s.planId)?.layout?.switches?.find(
                    (gs) => gs.sourceId === s.geometryId,
                );
                return {
                    asset: { type: 'GEOMETRY_SWITCH', id: s.geometryId },
                    title: geometrySwitchLayout?.name ?? '...',
                    element: (
                        <GeometrySwitchInfoboxContainer
                            visibilities={infoboxVisibilities.geometrySwitch}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('geometrySwitch', visibilities)
                            }
                            geometrySwitchId={s.geometryId}
                            geometryPlanId={s.planId}
                        />
                    ),
                };
            });

        const locationTrackTabs = locationTracks.map((track: LayoutLocationTrack): ToolPanelTab => {
            return {
                asset: { type: 'LOCATION_TRACK', id: track.id },
                title: track.name,
                element: (
                    <LocationTrackInfoboxLinkingContainer
                        visibilities={infoboxVisibilities.locationTrack}
                        onInfoboxVisibilityChange={infoboxVisibilityChange}
                        locationTrackId={track.id}
                        onDataChange={onDataChange}
                        onHoverOverPlanSection={onHoverOverPlanSection}
                    />
                ),
            };
        });

        const geometryAlignmentTabs = geometryAlignmentIds.map(
            (item: SelectedGeometryItem<GeometryAlignmentId>): ToolPanelTab => {
                const header = getPlan(item.planId)?.layout?.alignments?.find(
                    (a) => a.header.id === item.geometryId,
                )?.header;
                return {
                    asset: { type: 'GEOMETRY_ALIGNMENT', id: item.geometryId },
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
                            planId={item.planId}
                        />
                    ) : (
                        <Spinner />
                    ),
                };
            },
        );

        const operatingPointTabs: ToolPanelTab[] = operationalPoints.map((op) => {
            return {
                asset: {
                    type: 'OPERATING_POINT',
                    id: op.id,
                },
                title: op.name,
                element: (
                    <OperationalPointInfoboxContainer
                        operationalPointId={op.id}
                        visibilities={infoboxVisibilities.operationalPoint}
                        onVisiblityChange={(visibilities) =>
                            infoboxVisibilityChange('operationalPoint', visibilities)
                        }
                    />
                ),
            };
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
            ...operatingPointTabs,
        ].toSorted((t1, t2) =>
            compareByField(t1, t2, (t) => TOOL_PANEL_ASSET_ORDER.indexOf(t.asset.type)),
        );
        setTabs(allTabs);
    }, [
        planHeaders,
        trackNumbers,
        trackNumberIds,
        kmPosts,
        geometryKmPostIds,
        switches,
        geometrySwitchIds,
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

    const getLockedAsset = (): ToolPanelAsset | undefined => {
        if (linkingState?.type === LinkingType.LinkingAlignment) {
            const tabTypeToFind = linkingStateLayoutAlignmentTabType(
                linkingState.layoutAlignment.type,
            );

            return tabs.find(
                (t) =>
                    t.asset.type === tabTypeToFind &&
                    t.asset.id === linkingState.layoutAlignment.id,
            )?.asset;
        } else if (
            linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
            linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
            linkingState?.type === LinkingType.UnknownAlignment
        ) {
            return tabs.find(
                (t) =>
                    t.asset.type === 'GEOMETRY_ALIGNMENT' &&
                    t.asset.id === linkingState.geometryAlignmentId,
            )?.asset;
        } else if (linkingState?.type === LinkingType.LinkingGeometrySwitch) {
            return tabs.find((t) => {
                return (
                    (t.asset.type === 'GEOMETRY_SWITCH' || t.asset.type === 'SUGGESTED_SWITCH') &&
                    t.asset.id === SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID
                );
            })?.asset;
        } else if (linkingState?.type === LinkingType.LinkingLayoutSwitch) {
            return tabs.find((t) => {
                return (
                    t.asset.type === 'SUGGESTED_SWITCH' &&
                    t.asset.id === SUGGESTED_SWITCH_TOOL_PANEL_TAB_ID
                );
            })?.asset;
        } else if (linkingState?.type === LinkingType.LinkingKmPost) {
            return tabs.find((t) => {
                return (
                    t.asset.type === 'GEOMETRY_KM_POST' &&
                    t.asset.id === linkingState.geometryKmPostId
                );
            })?.asset;
        } else if (splittingState) {
            return tabs.find(
                (t) =>
                    t.asset.type === 'LOCATION_TRACK' &&
                    t.asset.id === splittingState.originLocationTrack.id,
            )?.asset;
        }
        return undefined;
    };

    function changeTab(tab: ToolPanelAsset) {
        const lockToAsset = getLockedAsset();
        if (!lockToAsset) setSelectedAsset(tab);
    }

    const lockedAsset = getLockedAsset();

    const getActiveTab = () => {
        const lockedTab = tabs.find((t) => isSameAsset(t.asset, lockedAsset));
        const selectedTab = tabs.find((t) => isSameAsset(t.asset, selectedAsset));
        const firstTab = first(tabs);

        return lockedTab ?? selectedTab ?? firstTab;
    };

    const activeTab = getActiveTab();
    return (
        <div className="tool-panel">
            {tabs.length > 1 && (
                <div className="tool-panel__tab-bar" qa-id="tool-panel-tabs">
                    {tabs.map((t, tabIndex) => {
                        const active = activeTab ? t.asset === activeTab.asset : tabIndex === 0;
                        return (
                            <TabHeader
                                className={'tool-panel__tab-header'}
                                size={TabHeaderSize.Small}
                                key={t.asset.type + '_' + t.asset.id}
                                selected={active}
                                onClick={() => changeTab(t.asset)}>
                                {t.title}
                            </TabHeader>
                        );
                    })}
                </div>
            )}
            {activeTab?.element}
            <LocationTrackTaskListContainer />
        </div>
    );
};

export default React.memo(ToolPanel);
