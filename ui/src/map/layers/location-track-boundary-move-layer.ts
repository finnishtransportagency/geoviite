import mapStyles from 'map/map.module.scss';
import { Circle, Fill, Stroke, Style } from 'ol/style';
import Feature from 'ol/Feature';
import { MapLayerName, MapTile } from 'map/map-model';
import {
    createLayer,
    GeoviiteMapLayer,
    loadLayerData,
    pointToCoords,
} from 'map/layers/utils/layer-utils';
import { LayoutContext } from 'common/common-model';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LineString, Point as OlPoint } from 'ol/geom';
import OlMap from 'ol/Map';
import { EventsKey } from 'ol/events';
import { unByKey } from 'ol/Observable';
import { getDefaultHitArea } from 'map/tools/tool-utils';
import { ChangingTrackBoundary, LinkingState, LinkingType } from 'linking/linking-model';
import {
    BoundaryOrientation,
    SelectedBoundaryMoveJoint,
} from 'track-layout/track-boundary-move-api';
import { getSelectedLocationTrackMapAlignmentByTiles } from 'track-layout/layout-map-api';
import { ChangeTimes } from 'common/common-slice';
import { EMPTY_ARRAY, filterUnique, indexIntoMap, nonEmptyArray } from 'utils/array-utils';
import {
    AlignmentPoint,
    LayoutSwitch,
    LayoutSwitchId,
    LocationTrackId,
    LocationTrackSwitchJoint,
    SwitchJointId,
} from 'track-layout/track-layout-model';
import {
    getLocationTrackStartAndEnd,
    getLocationTrackSwitchJoints,
} from 'track-layout/layout-location-track-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { expectDefined } from 'utils/type-utils';
import { Rectangle } from 'model/geometry';
import {
    boundaryMoveJointColor,
    BoundaryMoveTrackInfo,
    BoundaryMoveTrackInfos,
    BoundaryMoveTrackRole,
    computeBoundaryBar,
    findIntervalToMove,
    MoveInterval,
    moveMoveInterval,
} from 'map/layers/utils/location-track-boundary-move-layer-utils';

const layerName: MapLayerName = 'location-track-boundary-move-layer';

async function getTrackInfo(
    role: BoundaryMoveTrackRole,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    mapTiles: MapTile[],
    id: LocationTrackId,
): Promise<BoundaryMoveTrackInfo | undefined> {
    const alignmentPromise = getSelectedLocationTrackMapAlignmentByTiles(
        changeTimes,
        mapTiles,
        layoutContext,
        id,
    );
    const jointsAndSwitchesPromise = getLocationTrackSwitchJoints(layoutContext, id).then(
        async (joints) => {
            const switchIds = (joints ?? []).map((j) => j.switchId).filter(filterUnique);
            const switches = await getSwitches(switchIds, layoutContext, changeTimes.layoutSwitch);
            return { joints, switches };
        },
    );
    const startAndEndPromise = getLocationTrackStartAndEnd(
        id,
        layoutContext,
        changeTimes.layoutLocationTrack,
    );
    const [alignments, jointsAndSwitches, startAndEnd] = await Promise.all([
        alignmentPromise,
        jointsAndSwitchesPromise,
        startAndEndPromise,
    ]);
    const alignment = alignments[0];
    return alignment === undefined
        ? undefined
        : {
              role,
              alignment,
              joints: jointsAndSwitches.joints ?? EMPTY_ARRAY,
              switches: jointsAndSwitches.switches,
              startAndEnd,
          };
}

const ALIGNMENT_WIDTH = 3;
const JOINT_RADIUS = 8;

const BOUNDARY_BAR_DIRECTION_OFFSET = 4;
const BOUNDARY_BAR_HALF_LENGTH_PX = 20;

const headTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBoundaryMoveHeadTrack,
        width: ALIGNMENT_WIDTH,
    }),
});

const counterpartTrackStyle = new Style({
    stroke: new Stroke({
        color: mapStyles.alignmentBoundaryMoveCounterpartTrack,
        width: ALIGNMENT_WIDTH,
    }),
});

const headJointStyle = new Style({
    image: new Circle({
        radius: JOINT_RADIUS,
        fill: new Fill({ color: mapStyles.alignmentBoundaryMoveHeadTrack }),
    }),
    zIndex: 2,
});

const counterpartJointStyle = new Style({
    image: new Circle({
        radius: JOINT_RADIUS,
        fill: new Fill({ color: mapStyles.alignmentBoundaryMoveCounterpartTrack }),
    }),
    zIndex: 1,
});

const boundaryBarStyle = new Style({
    // Render the bar manually so its length stays fixed in screen pixels rather
    // than scaling with the map zoom. The geometry's two endpoints only provide
    // the (perpendicular) direction.
    renderer: (pixelCoordinates, state) => {
        const [start, end] = pixelCoordinates as [number[], number[]];
        const startX = expectDefined(start[0]);
        const startY = expectDefined(start[1]);
        let dx = expectDefined(end[0]) - startX;
        let dy = expectDefined(end[1]) - startY;
        const length = Math.hypot(dx, dy);
        if (length === 0) {
            return;
        }
        dx /= length;
        dy /= length;
        const midX = (startX + expectDefined(end[0])) / 2;
        const midY = (startY + expectDefined(end[1])) / 2;
        const half = BOUNDARY_BAR_HALF_LENGTH_PX * state.pixelRatio;
        const context = state.context;
        context.save();
        context.beginPath();
        context.moveTo(midX - dx * half, midY - dy * half);
        context.lineTo(midX + dx * half, midY + dy * half);
        context.strokeStyle = mapStyles.alignmentBoundaryMoveHeadTrack;
        context.lineWidth = ALIGNMENT_WIDTH * state.pixelRatio;
        context.stroke();
        context.restore();
    },
});

function isSelectableJoint(
    joint: LocationTrackSwitchJoint,
    switchesById: Map<LayoutSwitchId, LayoutSwitch>,
): boolean {
    const layoutSwitch = switchesById.get(joint.switchId);
    const switchJoint = layoutSwitch?.joints.find((j) => j.number === joint.jointNumber);
    return switchJoint?.role === 'MAIN';
}

function createJointFeaturesForTrack(
    track: BoundaryMoveTrackInfo,
    boundaryJointId: SwitchJointId | undefined,
    moveInterval: MoveInterval | undefined,
): Feature<LineString | OlPoint>[] {
    const switchesById = new Map(track.switches.map((s) => [s.id, s] as const));
    const features: Feature<LineString | OlPoint>[] = [];

    for (const joint of track.joints) {
        if (!isSelectableJoint(joint, switchesById)) continue;

        const color = boundaryMoveJointColor(track.role, joint, boundaryJointId, moveInterval);
        const style = color === 'head' ? headJointStyle : counterpartJointStyle;

        const feature = new Feature({
            geometry: new OlPoint(pointToCoords(joint.location)),
        });
        feature.setStyle(style);
        features.push(feature);
    }

    return features;
}

function createJointFeatures(
    trackInfos: BoundaryMoveTrackInfos,
    linkingState: ChangingTrackBoundary,
    moveInterval: MoveInterval | undefined,
): Feature<LineString | OlPoint>[] {
    const boundaryJointId: SwitchJointId | undefined =
        linkingState.selectedJoint?.joint ??
        linkingState.counterpart?.connectingSwitchJoint ??
        undefined;

    return [
        ...createJointFeaturesForTrack(trackInfos.headTrack, boundaryJointId, moveInterval),
        ...(trackInfos.counterpartTrack === undefined
            ? []
            : createJointFeaturesForTrack(
                  trackInfos.counterpartTrack,
                  boundaryJointId,
                  moveInterval,
              )),
    ];
}

function createBoundaryBarFeature(
    trackInfos: BoundaryMoveTrackInfos,
    orientation: BoundaryOrientation | undefined,
    selectedJoint: SelectedBoundaryMoveJoint | undefined,
): Feature<LineString | OlPoint> | undefined {
    const bar = computeBoundaryBar(trackInfos, orientation, selectedJoint);
    if (bar === undefined) {
        return undefined;
    }
    const { center, direction } = bar;
    const offsetX = direction.x * BOUNDARY_BAR_DIRECTION_OFFSET;
    const offsetY = direction.y * BOUNDARY_BAR_DIRECTION_OFFSET;
    const feature = new Feature({
        geometry: new LineString([
            [center.x - offsetX, center.y - offsetY],
            [center.x + offsetX, center.y + offsetY],
        ]),
    });
    feature.setStyle(boundaryBarStyle);
    return feature;
}

function findJointInTrackAt(
    track: BoundaryMoveTrackInfo,
    switchesById: Map<LayoutSwitchId, LayoutSwitch>,
    hitArea: Rectangle,
): LocationTrackSwitchJoint | undefined {
    for (const joint of track.joints) {
        const switchHasJoint = switchesById
            .get(joint.switchId)
            ?.joints.some((j) => j.number === joint.jointNumber);
        if (switchHasJoint && hitArea.containsXY(joint.location.x, joint.location.y)) {
            return joint;
        }
    }
    return undefined;
}

function alignmentFeature(points: AlignmentPoint[], style: Style): Feature<LineString | OlPoint> {
    const feature = new Feature({
        geometry: new LineString(points.map(pointToCoords)),
    });
    feature.setStyle(style);
    return feature;
}

function createFeatures(
    trackInfos: BoundaryMoveTrackInfos,
    linkingState: ChangingTrackBoundary,
): Feature<LineString | OlPoint>[] {
    const headPoints = trackInfos.headTrack.alignment.points;
    const counterpartPoints = trackInfos.counterpartTrack?.alignment.points ?? [];

    const orientation = linkingState.counterpart?.orientation;
    const moveInterval =
        linkingState.selectedJoint !== undefined && orientation !== undefined
            ? findIntervalToMove(trackInfos, orientation, linkingState.selectedJoint)
            : undefined;

    const { renderedHead, renderedCounterpart } =
        moveInterval === undefined || orientation === undefined
            ? { renderedHead: headPoints, renderedCounterpart: counterpartPoints }
            : moveMoveInterval(orientation, moveInterval, headPoints, counterpartPoints);

    return [
        ...nonEmptyArray(
            alignmentFeature(renderedCounterpart, counterpartTrackStyle),
            alignmentFeature(renderedHead, headTrackStyle),
        ),
        ...nonEmptyArray(
            createBoundaryBarFeature(trackInfos, orientation, linkingState.selectedJoint),
        ),
        ...createJointFeatures(trackInfos, linkingState, moveInterval),
    ];
}

export type LocationTrackBoundaryMoveLayer = MapLayer & {
    layer: GeoviiteMapLayer<LineString | OlPoint>;
    clickListenerKey: EventsKey;
};

export const createLocationTrackBoundaryMoveLayer = (
    mapTiles: MapTile[],
    existingLayer: LocationTrackBoundaryMoveLayer | undefined,
    layoutContext: LayoutContext,
    changeTimes: ChangeTimes,
    linkingState: LinkingState | undefined,
    olMap: OlMap,
    onSelectJoint: (joint: SelectedBoundaryMoveJoint) => void,
    onLoadingData: (loading: boolean) => void,
): LocationTrackBoundaryMoveLayer => {
    const { layer, source, isLatest } = createLayer(layerName, existingLayer?.layer);

    // A fresh listener is registered on every re-creation so it closes over the
    // current track infos and callbacks; detach the previous one first.
    if (existingLayer?.clickListenerKey) {
        unByKey(existingLayer.clickListenerKey);
    }

    const dataPromise: Promise<BoundaryMoveTrackInfos | undefined> = (async () => {
        if (linkingState?.type !== LinkingType.TrackBoundaryMove) {
            return undefined;
        }
        const [headTrack, counterpartTrack] = await Promise.all([
            getTrackInfo('head', layoutContext, changeTimes, mapTiles, linkingState.headTrack),
            linkingState.counterpart === undefined
                ? Promise.resolve(undefined)
                : getTrackInfo(
                      'counterpart',
                      layoutContext,
                      changeTimes,
                      mapTiles,
                      linkingState.counterpart?.trackId,
                  ),
        ]);
        return headTrack === undefined ? undefined : { headTrack, counterpartTrack };
    })();

    let loadedTrackInfos: BoundaryMoveTrackInfos | undefined = undefined;
    let switchesById: Map<LayoutSwitchId, LayoutSwitch> = new Map();
    loadLayerData(source, isLatest, onLoadingData, dataPromise, (trackInfos) => {
        if (linkingState?.type !== LinkingType.TrackBoundaryMove || trackInfos === undefined) {
            return [];
        }
        loadedTrackInfos = trackInfos;
        switchesById = indexIntoMap<LayoutSwitchId, LayoutSwitch>([
            ...trackInfos.headTrack.switches,
            ...(trackInfos.counterpartTrack?.switches ?? []),
        ]);
        return createFeatures(trackInfos, linkingState);
    });

    const clickListenerKey = olMap.on('click', ({ coordinate }) => {
        const trackInfos = loadedTrackInfos;
        if (trackInfos === undefined) {
            return;
        }
        const hitArea = getDefaultHitArea(olMap, coordinate);
        const onHead = findJointInTrackAt(trackInfos.headTrack, switchesById, hitArea);
        const onCounterpart =
            onHead === undefined && trackInfos.counterpartTrack !== undefined
                ? findJointInTrackAt(trackInfos.counterpartTrack, switchesById, hitArea)
                : undefined;
        const found = onHead ?? onCounterpart;
        if (found !== undefined && isSelectableJoint(found, switchesById)) {
            onSelectJoint({
                role: onHead !== undefined ? 'head' : 'counterpart',
                joint: { switchId: found.switchId, jointNumber: found.jointNumber },
            });
        }
    });

    return {
        name: layerName,
        layer: layer,
        clickListenerKey,
        onRemove: () => unByKey(clickListenerKey),
    };
};
