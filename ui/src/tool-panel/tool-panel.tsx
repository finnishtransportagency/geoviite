import * as React from 'react';
import GeometryPlanInfobox from 'tool-panel/geometry-plan-infobox';
import { GeometryPlanHeader, GeometryPlanId, GeometrySwitchId } from 'geometry/geometry-model';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    DraftType,
    LayoutKmPost,
    LayoutKmPostId,
    LayoutLocationTrack,
    LayoutSwitch,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    MapAlignment,
    MapSegment,
} from 'track-layout/track-layout-model';
import KmPostInfobox from 'tool-panel/km-post/km-post-infobox';
import SwitchInfobox from 'tool-panel/switch/switch-infobox';
import GeometrySwitchInfobox from 'tool-panel/switch/geometry-switch-infobox';
import { LinkingState, LinkingType, SuggestedSwitch } from 'linking/linking-model';
import {
    OptionalUnselectableItemCollections,
    SelectedGeometryItem,
} from 'selection/selection-model';
import { BoundingBox, Point } from 'model/geometry';
import GeometryAlignmentLinkingContainer from 'tool-panel/geometry-alignment/geometry-alignment-linking-container';
import { PublishType } from 'common/common-model';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import LocationTrackInfoboxLinkingContainer from 'tool-panel/location-track/location-track-infobox-linking-container';
import { getKmPosts } from 'track-layout/layout-km-post-api';
import TrackNumberInfoboxLinkingContainer from 'tool-panel/track-number/track-number-infobox-linking-container';
import { useLoader } from 'utils/react-utils';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getLocationTracks } from 'track-layout/layout-location-track-api';
import { MapViewport } from 'map/map-model';
import { getGeometryPlanHeaders } from 'geometry/geometry-api';
import { ChangeTimes } from 'common/common-slice';
import {
    GeometryKmPostInfoboxVisibilities,
    InfoboxVisibilities,
} from 'track-layout/track-layout-slice';
import GeometryKmPostInfobox from 'tool-panel/km-post/geometry-km-post-infobox';

type ToolPanelProps = {
    planIds: GeometryPlanId[];
    trackNumberIds: LayoutTrackNumberId[];
    kmPostIds: LayoutKmPostId[];
    geometryKmPosts: SelectedGeometryItem<LayoutKmPost>[];
    switchIds: LayoutSwitchId[];
    geometrySwitches: SelectedGeometryItem<LayoutSwitch>[];
    locationTrackIds: LocationTrackId[];
    geometryAlignments: SelectedGeometryItem<MapAlignment>[];
    geometrySegments: SelectedGeometryItem<MapSegment>[];
    suggestedSwitches: SuggestedSwitch[];
    linkingState?: LinkingState;
    showArea: (bbox: BoundingBox) => void;
    changeTimes: ChangeTimes;
    publishType: PublishType;
    onDataChange: () => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    selectedTabId: string | undefined;
    setSelectedTabId: (id: string | undefined) => void;
    startSwitchPlacing: (layoutSwitch: LayoutSwitch) => void;
    viewport: MapViewport;
    infoboxVisibilities: InfoboxVisibilities;
    onInfoboxVisibilityChange: (visibilities: InfoboxVisibilities) => void;
};

type ToolPanelTab = {
    id: GeometryPlanId;
    title: string;
    element: React.ReactElement;
};

export function toolPanelPlanTabId(planId: GeometryPlanId): string {
    return 'plan-header_' + planId;
}

const ToolPanel: React.FC<ToolPanelProps> = ({
    planIds,
    trackNumberIds,
    kmPostIds,
    geometryKmPosts,
    switchIds,
    geometrySwitches,
    locationTrackIds,
    geometryAlignments,
    geometrySegments,
    suggestedSwitches,
    linkingState,
    showArea,
    changeTimes,
    publishType,
    onDataChange,
    onUnselect,
    selectedTabId,
    setSelectedTabId,
    startSwitchPlacing,
    viewport,
    infoboxVisibilities,
    onInfoboxVisibilityChange,
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

        return Promise.all([
            locationTracksPromise.then((l) => l.filter(filterNotEmpty)),
            switchesPromise.then((l) => l.filter(filterNotEmpty)),
            kmPostsPromise.then((l) => l.filter(filterNotEmpty)),
            trackNumbersPromise.then((l) => l.filter(filterNotEmpty)),
            plansPromise.then((l) => l.filter(filterNotEmpty)),
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
    ]);

    const locationTracks = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[0]) || [];
    const switches = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[1]) || [];
    const kmPosts = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[2]) || [];
    const trackNumbers = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[3]) || [];
    const planHeaders = (tracksSwitchesKmPostsPlans && tracksSwitchesKmPostsPlans[4]) || [];

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
                id: toolPanelPlanTabId(p.id),
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
            };
        });

        const trackNumberTabs = trackNumberIds
            .map((tnId) => trackNumbers?.find((tn) => tn.id === tnId))
            .filter(filterNotEmpty)
            .map((t) => {
                return {
                    id: 'track-number_' + t.id,
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
                        />
                    ),
                };
            });

        const layoutKmPostTabs = kmPosts.filter(visibleByTypeAndPublishType).map((k) => {
            return {
                id: 'km-post_' + k.id,
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
            };
        });

        const geometryKmPostTabs = geometryKmPosts.map((k) => {
            return {
                id: 'geometry-km-post_' + k.geometryItem.id,
                title: k.geometryItem.kmNumber,
                element: (
                    <GeometryKmPostInfobox
                        geometryKmPost={k.geometryItem}
                        planId={k.planId}
                        onShowOnMap={() =>
                            k.geometryItem.location &&
                            showArea(
                                calculateBoundingBoxToShowAroundLocation(k.geometryItem.location),
                            )
                        }
                        visibilities={infoboxVisibilities.geometryKmPost}
                        onVisibilityChange={(visibilities: GeometryKmPostInfoboxVisibilities) =>
                            infoboxVisibilityChange('geometryKmPost', visibilities)
                        }
                    />
                ),
            };
        });

        const switchTabs = switches.filter(visibleByTypeAndPublishType).map((s) => {
            return {
                id: 'switch_' + s.id,
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
                    />
                ),
            };
        });

        const uniqueGeometrySwitches = [
            ...suggestedSwitches.map((s) => ({
                id: s.id,
                name: s.name,
                switchId: s.geometrySwitchId,
                planId: s.geometryPlanId,
                suggestedSwitch: s,
            })),
            ...geometrySwitches.map((s) => ({
                id: s.geometryItem.sourceId as GeometrySwitchId,
                name: s.geometryItem.name,
                switchId: s.geometryItem.sourceId as GeometrySwitchId,
                planId: s.planId,
                suggestedSwitch: undefined,
            })),
        ].filter(filterUniqueById((i) => i.switchId));

        const geometrySwitchTabs = uniqueGeometrySwitches.map((s) => {
            return {
                id: 'geometry-switch_' + s.id,
                title: s.name || '-',
                element: (
                    <GeometrySwitchInfobox
                        visibilities={infoboxVisibilities.geometrySwitch}
                        onVisibilityChange={(visibilities) =>
                            infoboxVisibilityChange('geometrySwitch', visibilities)
                        }
                        switchId={s.switchId ?? undefined}
                        layoutSwitch={switches ? switches[0] : undefined}
                        suggestedSwitch={s.suggestedSwitch}
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
                id: 'location-track_' + track.id,
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
                    />
                ),
            };
        });

        const geometryAlignmentTabs = geometryAlignments.map((a) => {
            return {
                id: 'geometry-alignment_' + a.geometryItem.id,
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
                        segment={geometrySegments[0]?.geometryItem}
                        planId={a.planId}
                        linkingState={linkingState}
                        publishType={publishType}
                    />
                ),
            };
        });

        const allTabs = [
            ...geometryKmPostTabs,
            ...layoutKmPostTabs,
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
        geometryKmPosts,
        switches,
        geometrySwitches,
        suggestedSwitches,
        locationTracks,
        geometryAlignments,
        linkingState,
        publishType,
        viewport,
        changeTimes,
        infoboxVisibilities,
    ]);

    React.useEffect(() => {
        const newTabs = tabs.filter((t) => !previousTabs.some((pt) => t.id == pt.id));

        if (newTabs.length) {
            if (selectedTabId && newTabs.some((nt) => nt.id == selectedTabId)) {
                changeTab(selectedTabId);
            } else {
                changeTab(tabs[0].id);
            }
        }
        setPreviousTabs(tabs);
    }, [tabs]);

    function changeTab(tabId: string) {
        let lockToTabId;

        if (linkingState?.type === LinkingType.LinkingAlignment) {
            lockToTabId = tabs.find(
                (t) => t.id === 'location-track_' + linkingState.layoutAlignmentId,
            )?.id;
        } else if (
            linkingState?.type === LinkingType.LinkingGeometryWithEmptyAlignment ||
            linkingState?.type === LinkingType.LinkingGeometryWithAlignment ||
            linkingState?.type === LinkingType.UnknownAlignment
        ) {
            lockToTabId = tabs.find(
                (t) => t.id === 'geometry-alignment_' + linkingState.geometryAlignmentId,
            )?.id;
        } else if (linkingState?.type === LinkingType.LinkingSwitch) {
            lockToTabId = tabs.find((t) => {
                return (
                    t.id === 'geometry-switch_' + linkingState.suggestedSwitch.geometrySwitchId ||
                    suggestedSwitches.some((s) => t.id === 'geometry-switch_' + s.id)
                );
            })?.id;
        } else if (linkingState?.type === LinkingType.LinkingKmPost) {
            lockToTabId = tabs.find((t) => {
                return t.id === 'geometry-km-post_' + linkingState.geometryKmPostId;
            })?.id;
        }

        setSelectedTabId(lockToTabId ? lockToTabId : tabId);
    }

    return (
        <div className="tool-panel">
            {tabs.length > 1 && (
                <div className="infobox-tabs" qa-id="tool-panel-tabs">
                    {tabs.map((t) => {
                        return (
                            <Button
                                key={t.id}
                                variant={
                                    selectedTabId == t.id
                                        ? ButtonVariant.PRIMARY
                                        : ButtonVariant.SECONDARY
                                }
                                size={ButtonSize.SMALL}
                                onClick={() => changeTab(t.id)}
                                isPressed={t.id == selectedTabId}>
                                {t.title}
                            </Button>
                        );
                    })}
                </div>
            )}
            {tabs.find((t) => t.id === selectedTabId)?.element || tabs[0]?.element}
        </div>
    );
};

export default React.memo(ToolPanel);
