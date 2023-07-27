import * as React from 'react';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import {
    GeometryKmPostId,
    GeometryPlanHeader,
    GeometryPlanId,
    GeometrySwitchId,
} from 'geometry/geometry-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    DraftType,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import KmPostInfobox from 'tool-panel/km-post/km-post-infobox';
import SwitchInfobox from 'tool-panel/switch/switch-infobox';
import GeometrySwitchInfobox from 'tool-panel/switch/geometry-switch-infobox';
import { LinkingState, LinkingType, SuggestedSwitch } from 'linking/linking-model';
import {
    OptionalUnselectableItemCollections,
    SelectedGeometryItem,
    SelectedGeometryItemId,
} from 'selection/selection-model';
import { BoundingBox, Point } from 'model/geometry';
import GeometryAlignmentLinkingContainer from 'tool-panel/geometry-alignment/geometry-alignment-linking-container';
import { PublishType } from 'common/common-model';
import { filterNotEmpty, filterUnique } from 'utils/array-utils';
import LocationTrackInfoboxLinkingContainer from 'tool-panel/location-track/location-track-infobox-linking-container';
import { getKmPosts } from 'track-layout/layout-km-post-api';
import TrackNumberInfoboxLinkingContainer from 'tool-panel/track-number/track-number-infobox-linking-container';
import { useLoader } from 'utils/react-utils';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { MapViewport } from 'map/map-model';
import { getGeometryPlanHeaders, getTrackLayoutPlansByIds } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import {
    GeometryKmPostInfoboxVisibilities,
    InfoboxVisibilities,
} from 'track-layout/track-layout-slice';
import GeometryKmPostInfobox from 'tool-panel/km-post/geometry-km-post-infobox';
import { AlignmentHeader } from 'track-layout/layout-map-api';
import { HighlightedAlignment } from 'tool-panel/alignment-plan-section-infobox-content';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type ToolPanelProps = {
    planIds: GeometryPlanId[];
    trackNumberIds: LayoutTrackNumberId[];
    kmPostIds: LayoutKmPostId[];
    geometryKmPostIds: SelectedGeometryItemId<GeometryKmPostId>[];
    switchIds: LayoutSwitchId[];
    geometrySwitchIds: SelectedGeometryItemId<GeometrySwitchId>[];
    locationTrackIds: LocationTrackId[];
    geometryAlignments: SelectedGeometryItem<AlignmentHeader>[];
    suggestedSwitches: SuggestedSwitch[];
    linkingState?: LinkingState;
    showArea: (bbox: BoundingBox) => void;
    changeTimes: ChangeTimes;
    publishType: PublishType;
    onDataChange: () => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    selectedAsset: ToolPanelAsset | undefined;
    setSelectedAsset: (id: ToolPanelAsset | undefined) => void;
    startSwitchPlacing: (layoutSwitch: LayoutSwitch) => void;
    viewport: MapViewport;
    infoboxVisibilities: InfoboxVisibilities;
    onInfoboxVisibilityChange: (visibilities: InfoboxVisibilities) => void;
    stopSwitchLinking: () => void;
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
    geometryAlignments,
    suggestedSwitches,
    linkingState,
    showArea,
    changeTimes,
    publishType,
    onDataChange,
    onUnselect,
    selectedAsset,
    setSelectedAsset,
    startSwitchPlacing,
    viewport,
    infoboxVisibilities,
    onInfoboxVisibilityChange,
    stopSwitchLinking,
    verticalGeometryDiagramVisible,
    onHoverOverPlanSection,
}: ToolPanelProps) => {
    const [previousTabs, setPreviousTabs] = React.useState<ToolPanelTab[]>([]);
    const [tabs, setTabs] = React.useState<ToolPanelTab[]>([]);

    const onShowMapLocation = React.useCallback(
        (location: Point) => showArea(calculateBoundingBoxToShowAroundLocation(location)),
        [],
    );

    const onUnSelectLocationTracks = React.useCallback((track: LayoutLocationTrack) => {
        onUnselect({ locationTracks: [track.id] });
    }, []);

    const onUnSelectSwitches = React.useCallback((switchId: LayoutSwitchId) => {
        onUnselect({ switches: [switchId] });
    }, []);

    const tracksSwitchesKmPostsPlans = useLoader(() => {
        const trackNumbersPromise = getTrackNumbers(publishType, changeTimes.layoutTrackNumber);
        const locationTracksPromise = getLocationTracks(locationTrackIds, publishType);
        const switchesPromise = getSwitches(switchIds, publishType);
        const kmPostsPromise = getKmPosts(kmPostIds, publishType);
        const plansPromise = getGeometryPlanHeaders(planIds);
        const elementPlanIds = [
            ...geometryKmPostIds.map((kmp) => kmp.planId),
            ...geometrySwitchIds.map((s) => s.planId),
        ].filter(filterUnique);
        const elementPlansPromise = getTrackLayoutPlansByIds(
            elementPlanIds,
            changeTimes.geometryPlan,
            false,
        );

        return Promise.all([
            // TODO: GVT-826 Check the nullability in these api-calls/caches.
            // It is possible for an item in the id-list to not exist, but these functions do not typically return null in that case.
            // The whole thing needs a check-up. What's the correct handling?
            // Don't double-check nulls and make sure that types match what is returned.
            locationTracksPromise.then((l) => l.filter(filterNotEmpty)),
            switchesPromise.then((l) => l.filter(filterNotEmpty)),
            kmPostsPromise.then((l) => l.filter(filterNotEmpty)),
            trackNumbersPromise.then((l) => l.filter(filterNotEmpty)),
            plansPromise.then((l) => l.filter(filterNotEmpty)),
            elementPlansPromise,
        ]);
    }, [
        locationTrackIds,
        changeTimes.layoutLocationTrack,
        publishType,
        switchIds,
        changeTimes.layoutSwitch,
        kmPostIds,
        changeTimes.layoutKmPost,
        changeTimes.layoutTrackNumber,
        planIds,
        geometryKmPostIds,
        geometrySwitchIds,
    ]);

    const locationTracks = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[0]) || [];
    const switches = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[1]) || [];
    const kmPosts = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[2]) || [];
    const trackNumbers = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[3]) || [];
    const planHeaders = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[4]) || [];

    const getPlan = (id: GeometryPlanId) =>
        tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[5].find((p) => p.planId === id);

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
    const visibleByTypeAndPublishType = ({ draftType }: { draftType: DraftType }) =>
        publishType === 'DRAFT' || draftType !== 'NEW_DRAFT';

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
            .map((t) => {
                return {
                    asset: { type: 'TRACK_NUMBER', id: t.id },
                    title: t.number,
                    element: (
                        <TrackNumberInfoboxLinkingContainer
                            visibilities={infoboxVisibilities.trackNumber}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('trackNumber', visibilities)
                            }
                            trackNumber={t}
                            publishType={publishType}
                            linkingState={linkingState}
                            onUnselect={onUnselect}
                            referenceLineChangeTime={changeTimes.layoutReferenceLine}
                            viewport={viewport}
                            onHoverOverPlanSection={onHoverOverPlanSection}
                        />
                    ),
                } as ToolPanelTab;
            });

        const layoutKmPostTabs = kmPosts.filter(visibleByTypeAndPublishType).map((k) => {
            return {
                asset: { type: 'KM_POST', id: k.id },
                title: k.kmNumber,
                element: (
                    <KmPostInfobox
                        visibilities={infoboxVisibilities.kmPost}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('kmPost', visibilities)
                        }
                        publishType={publishType}
                        kmPostChangeTime={changeTimes.layoutKmPost}
                        onDataChange={onDataChange}
                        kmPost={k}
                        onUnselect={() => {
                            onUnselect({
                                kmPosts: [k.id],
                            });
                        }}
                        onShowOnMap={() =>
                            k.location &&
                            showArea(calculateBoundingBoxToShowAroundLocation(k.location))
                        }
                    />
                ),
            } as ToolPanelTab;
        });

        const geometryKmPostTabs = geometryKmPostIds.map(
            (k: SelectedGeometryItemId<LayoutKmPostId>) => {
                const kmPost = getPlan(k.planId)?.kmPosts?.find((p) => p.sourceId === k.geometryId);
                return {
                    asset: { type: 'GEOMETRY_KM_POST', id: k.geometryId },
                    title: kmPost?.kmNumber ?? '...',
                    element: kmPost ? (
                        <GeometryKmPostInfobox
                            geometryKmPost={kmPost}
                            planId={k.planId}
                            onShowOnMap={() =>
                                kmPost.location &&
                                showArea(calculateBoundingBoxToShowAroundLocation(kmPost.location))
                            }
                            visibilities={infoboxVisibilities.geometryKmPost}
                            onVisibilityChange={(visibilities: GeometryKmPostInfoboxVisibilities) =>
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
                    <SwitchInfobox
                        visibilities={infoboxVisibilities.switch}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('switch', visibilities)
                        }
                        switchId={s.id}
                        onShowOnMap={onShowMapLocation}
                        publishType={publishType}
                        changeTimes={changeTimes}
                        onDataChange={onDataChange}
                        onUnselect={onUnSelectSwitches}
                        placingSwitchLinkingState={
                            linkingState?.type == LinkingType.PlacingSwitch
                                ? linkingState
                                : undefined
                        }
                        startSwitchPlacing={startSwitchPlacing}
                        stopLinking={stopSwitchLinking}
                    />
                ),
            } as ToolPanelTab;
        });

        const suggestedSwitchTabs: ToolPanelTab[] = suggestedSwitches.map((ss) => {
            return {
                asset: { type: 'GEOMETRY_SWITCH', id: ss.id },
                title: ss.name || '-',
                element: (
                    <GeometrySwitchInfobox
                        visibilities={infoboxVisibilities.geometrySwitch}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometrySwitch', visibilities)
                        }
                        switchId={ss.geometrySwitchId ?? undefined}
                        layoutSwitch={switches ? switches[0] : undefined}
                        suggestedSwitch={ss}
                        linkingState={linkingState}
                        planId={ss.geometryPlanId ?? undefined}
                        switchChangeTime={changeTimes.layoutSwitch}
                        locationTrackChangeTime={changeTimes.layoutLocationTrack}
                        onShowOnMap={onShowMapLocation}
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
                    title: geomSwitch ? geomSwitch.name || '-' : '...',
                    element: (
                        <GeometrySwitchInfobox
                            visibilities={infoboxVisibilities.geometrySwitch}
                            onVisibilityChange={(visibilities) =>
                                infoboxVisibilityChange('geometrySwitch', visibilities)
                            }
                            switchId={s.geometryId}
                            layoutSwitch={switches ? switches[0] : undefined}
                            suggestedSwitch={undefined}
                            linkingState={linkingState}
                            planId={s.planId ?? undefined}
                            switchChangeTime={changeTimes.layoutSwitch}
                            locationTrackChangeTime={changeTimes.layoutLocationTrack}
                            onShowOnMap={onShowMapLocation}
                        />
                    ),
                };
            });

        const locationTrackTabs = locationTracks.map((track) => {
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
                        linkingState={linkingState}
                        publishType={publishType}
                        locationTrackChangeTime={changeTimes.layoutLocationTrack}
                        onDataChange={onDataChange}
                        onUnselect={onUnSelectLocationTracks}
                        viewport={viewport}
                        verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
                        onHoverOverPlanSection={onHoverOverPlanSection}
                    />
                ),
            } as ToolPanelTab;
        });

        const geometryAlignmentTabs = geometryAlignments.map((a) => {
            return {
                asset: { type: 'GEOMETRY_ALIGNMENT', id: a.geometryItem.id },
                title: a.geometryItem.name,
                element: (
                    <GeometryAlignmentLinkingContainer
                        visibilities={infoboxVisibilities.geometryAlignment}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometryAlignment', visibilities)
                        }
                        geometryAlignment={a.geometryItem}
                        selectedLocationTrackId={locationTrackIds[0]}
                        selectedTrackNumberId={trackNumberIds[0]}
                        planId={a.planId}
                        linkingState={linkingState}
                        publishType={publishType}
                        verticalGeometryDiagramVisible={verticalGeometryDiagramVisible}
                    />
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
        geometryAlignments,
        linkingState,
        publishType,
        viewport,
        changeTimes,
        infoboxVisibilities,
        verticalGeometryDiagramVisible,
    ]);

    React.useEffect(() => {
        const newTabs = tabs.filter(
            (t) => !previousTabs.some((pt) => isSameAsset(t.asset, pt.asset)),
        );

        if (newTabs.length) {
            if (selectedAsset && newTabs.some((nt) => isSameAsset(nt.asset, selectedAsset))) {
                changeTab(selectedAsset);
            } else {
                changeTab(tabs[0].asset);
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

    return (
        <div className="tool-panel">
            {tabs.length > 1 && (
                <div className="infobox-tabs" qa-id="tool-panel-tabs">
                    {tabs.map((t) => {
                        return (
                            <Button
                                key={t.asset.type + '_' + t.asset.id}
                                variant={
                                    isSameAsset(t.asset, selectedAsset)
                                        ? ButtonVariant.PRIMARY
                                        : ButtonVariant.SECONDARY
                                }
                                size={ButtonSize.SMALL}
                                onClick={() => changeTab(t.asset)}
                                isPressed={isSameAsset(t.asset, selectedAsset)}>
                                {t.title}
                            </Button>
                        );
                    })}
                </div>
            )}
            {tabs.find((t) => isSameAsset(t.asset, selectedAsset))?.element || tabs[0]?.element}
        </div>
    );
};

export default React.memo(ToolPanel);
