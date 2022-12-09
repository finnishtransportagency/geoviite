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
import { ChangeTimes } from 'track-layout/track-layout-store';
import GeometryAlignmentLinkingContainer from 'tool-panel/geometry-alignment/geometry-alignment-linking-container';
import { PublishType } from 'common/common-model';
import { filterNotEmpty, filterUniqueById } from 'utils/array-utils';
import GeometryKmPostInfoboxContainer from 'tool-panel/km-post/geometry-km-post-infobox-container';
import LocationTrackInfoboxLinkingContainer from 'tool-panel/location-track/location-track-infobox-linking-container';
import { getKmPost } from 'track-layout/layout-km-post-api';
import TrackNumberInfoboxLinkingContainer from 'tool-panel/track-number/track-number-infobox-linking-container';
import { useLoader } from 'utils/react-utils';
import { calculateBoundingBoxToShowAroundLocation } from 'map/map-utils';
import { getTrackNumbers } from 'track-layout/layout-track-number-api';
import { getSwitch } from 'track-layout/layout-switch-api';
import { getLocationTrack } from 'track-layout/layout-location-track-api';

type ToolPanelProps = {
    planHeaders: GeometryPlanHeader[];
    trackNumberIds: LayoutTrackNumberId[];
    kmPostIds: LayoutKmPostId[];
    geometryKmPosts: SelectedGeometryItem<LayoutKmPost>[];
    switchIds: LayoutSwitchId[];
    geometrySwitches: SelectedGeometryItem<LayoutSwitch>[];
    locationTrackIds: LocationTrackId[];
    // referenceLineIds: ReferenceLineId[];
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
};

type ToolPanelTab = {
    id: GeometryPlanId;
    title: string;
    element: React.ReactElement;
};

const ToolPanel: React.FC<ToolPanelProps> = ({
    planHeaders,
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
}: ToolPanelProps) => {
    console.log('Tool-Panel bind', 'trackNumbers', trackNumberIds, 'referenceLines');

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
        onUnselect({
            switches: [switchId],
        });
    }, []);

    const tracksSwitchesKmPosts = useLoader(() => {
        const trackNumbersPromise = getTrackNumbers(publishType, changeTimes.layoutTrackNumber);
        // Data accessed ToolPanel is most likely cached and mass fetches don't have caching implemented yet.
        // These should be switched to using mass fetches once GVT-1428 is done
        const locationTracksPromise = Promise.all(
            locationTrackIds.map((id) =>
                getLocationTrack(id, publishType, changeTimes.layoutLocationTrack),
            ),
        );

        const switchesPromise = Promise.all(switchIds?.map((swId) => getSwitch(swId, publishType)));

        const kmPostsPromise = Promise.all(
            kmPostIds?.map((kmPostId) =>
                getKmPost(kmPostId, publishType, changeTimes.layoutKmPost),
            ),
        );

        return Promise.all([
            locationTracksPromise.then((l) => l.filter(filterNotEmpty)),
            switchesPromise.then((l) => l.filter(filterNotEmpty)),
            kmPostsPromise.then((l) => l.filter(filterNotEmpty)),
            trackNumbersPromise.then((l) => l.filter(filterNotEmpty)),
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
    ]);

    const locationTracks = (tracksSwitchesKmPosts && tracksSwitchesKmPosts[0]) || [];
    const switches = (tracksSwitchesKmPosts && tracksSwitchesKmPosts[1]) || [];
    const kmPosts = (tracksSwitchesKmPosts && tracksSwitchesKmPosts[2]) || [];
    const trackNumbers = (tracksSwitchesKmPosts && tracksSwitchesKmPosts[3]) || [];

    // Draft-only entities should be hidden when viewing in official mode. Show everything in draft mode
    const visibleByTypeAndPublishType = ({ draftType }: { draftType: DraftType }) =>
        publishType === 'DRAFT' || draftType !== 'NEW_DRAFT';

    React.useEffect(() => {
        if (tracksSwitchesKmPosts === undefined) {
            return;
        }
        const planTabs = planHeaders.map((p: GeometryPlanHeader) => {
            return {
                id: 'plan-header_' + p.id,
                title: p.fileName,
                element: <GeometryPlanInfobox planHeader={p} />,
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
                            trackNumber={t}
                            publishType={publishType}
                            linkingState={linkingState}
                            onUnselect={onUnselect}
                            referenceLineChangeTime={changeTimes.layoutReferenceLine}
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
                element: <GeometryKmPostInfoboxContainer geometryKmPost={k} showArea={showArea} />,
            };
        });

        const switchTabs = switches.filter(visibleByTypeAndPublishType).map((s) => {
            return {
                id: 'switch_' + s.id,
                title: s.name,
                element: (
                    <SwitchInfobox
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
                        locationTrackId={track.id}
                        linkingState={linkingState}
                        publishType={publishType}
                        locationTrackChangeTime={changeTimes.layoutLocationTrack}
                        onDataChange={onDataChange}
                        onUnselect={onUnSelectLocationTracks}
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
        changeTimes,
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
                <div qa-id="tool-panel-tabs">
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
