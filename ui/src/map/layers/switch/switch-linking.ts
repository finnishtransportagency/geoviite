import { coordsToPoint, Point } from 'model/geometry';
import { asyncCache, AsyncCache } from 'cache/cache';
import { LinkingState, LinkingType, SuggestedSwitch } from 'linking/linking-model';
import React from 'react';
import OlMap from 'ol/Map';
import { LayoutContext } from 'common/common-model';
import { useLimitedRequestsInFlight } from 'utils/react-utils';
import { grid, pointEquals } from 'utils/math-utils';
import { getPlanarDistance } from 'map/layers/utils/layer-utils';
import { pointString } from 'common/common-api';
import { getSuggestedSwitchesForLayoutSwitchPlacing } from 'linking/linking-api';
import { ScreenPoint } from 'map/map-view';

export const SUGGESTED_SWITCHES_GRID_SIZE = 5;

type ScreenGrid = {
    cellIndex: Point;
    positionInCell: Point;
};

type SuggestedSwitchCache = {
    cache: AsyncCache<string, (undefined | SuggestedSwitch)[]>;
    resolution: number;
    center: Point;
};

function tilePoints(olMap: OlMap, suggestedSwitchesGridCellIndex: Point): Point[] {
    return [...Array(SUGGESTED_SWITCHES_GRID_SIZE)].flatMap((_, yIndex) =>
        [...Array(SUGGESTED_SWITCHES_GRID_SIZE)].map((_, xIndex) =>
            coordsToPoint(
                olMap.getCoordinateFromPixel([
                    suggestedSwitchesGridCellIndex.x * SUGGESTED_SWITCHES_GRID_SIZE + xIndex,
                    suggestedSwitchesGridCellIndex.y * SUGGESTED_SWITCHES_GRID_SIZE + yIndex,
                ]),
            ),
        ),
    );
}

export function useSwitchLinking(
    _setHoveredLocation: (point: Point) => void,
    setHoveredPixelLocation: (point: ScreenPoint) => void,
    olMapContainer: React.RefObject<HTMLDivElement>,
    olMap: OlMap | undefined,
    hoveredPixelLocation: ScreenPoint | undefined,
    linkingState: LinkingState | undefined,
    layoutContext: LayoutContext,
    suggestSwitchAndDisplaySwitchLinkingLayer: (
        suggestedSwitch: SuggestedSwitch | undefined,
    ) => void,
): {
    setHoveredLocation: (newHoveredLocation: Point, pixelPosition: ScreenPoint) => void;
    isLoadingSwitchSuggestion: boolean;
} {
    const [isLoadingSwitchSuggestion, setIsLoadingSwitchSuggestion] = React.useState(false);
    const positionInSuggestedSwitchGrid = React.useRef<ScreenGrid>();

    const requestLoadingSuggestedSwitch = useLimitedRequestsInFlight<
        (SuggestedSwitch | undefined)[]
    >('stack', 1);

    const suggestedSwitchCache = React.useRef<SuggestedSwitchCache>();

    const view = olMap?.getView();
    const viewCenterX = view?.getCenter()?.[0];
    const viewCenterY = view?.getCenter()?.[1];
    const viewResolution = view?.getResolution();
    React.useEffect(() => {
        if (
            view &&
            viewCenterX !== undefined &&
            viewCenterY !== undefined &&
            viewResolution !== undefined
        ) {
            const current = suggestedSwitchCache.current;
            if (
                current === undefined ||
                current.resolution !== viewResolution ||
                viewCenterX !== current.center.x ||
                viewCenterY !== current.center.y
            ) {
                suggestedSwitchCache.current = {
                    cache: asyncCache(),
                    resolution: viewResolution,
                    center: { x: viewCenterX, y: viewCenterY },
                };
            }
        }
    }, [viewCenterX, viewCenterY, viewResolution]);

    function processLoadedTileSwitches(
        suggestedSwitchCacheAtRequestTime: SuggestedSwitchCache,
        suggestedSwitchesGrid: { cellIndex: Point; positionInCell: Point },
        tileSwitches: (SuggestedSwitch | undefined)[],
        hoveredLocation: Point,
    ): void {
        if (
            suggestedSwitchCacheAtRequestTime === suggestedSwitchCache.current &&
            positionInSuggestedSwitchGrid.current !== undefined &&
            pointEquals(
                positionInSuggestedSwitchGrid.current.cellIndex,
                suggestedSwitchesGrid.cellIndex,
            )
        ) {
            setIsLoadingSwitchSuggestion(false);
            const switchIndexInCell =
                suggestedSwitchesGrid.positionInCell.y * SUGGESTED_SWITCHES_GRID_SIZE +
                suggestedSwitchesGrid.positionInCell.x;
            const suggested = tileSwitches[switchIndexInCell];
            if (
                suggested &&
                hoveredLocation &&
                suggested.joints[0] !== undefined &&
                getPlanarDistance(suggested.joints[0].location, hoveredLocation) < 10
            ) {
                suggestSwitchAndDisplaySwitchLinkingLayer(tileSwitches[switchIndexInCell]);
            } else {
                suggestSwitchAndDisplaySwitchLinkingLayer(undefined);
            }
        }
    }

    return {
        isLoadingSwitchSuggestion,
        setHoveredLocation: (hoveredLocation: Point, pixelPosition: ScreenPoint) => {
            _setHoveredLocation(hoveredLocation);
            setHoveredPixelLocation(pixelPosition);

            const container = olMapContainer.current;
            if (
                olMap !== undefined &&
                hoveredPixelLocation !== undefined &&
                container !== null &&
                (linkingState?.type === LinkingType.PlacingSwitch ||
                    linkingState?.type === LinkingType.SuggestingSwitchPlace)
            ) {
                const rect = container.getBoundingClientRect();
                const pixel = {
                    x: hoveredPixelLocation.x - rect.x,
                    y: hoveredPixelLocation.y - rect.y,
                };
                const suggestedSwitchesGrid = grid(SUGGESTED_SWITCHES_GRID_SIZE, pixel);
                positionInSuggestedSwitchGrid.current = suggestedSwitchesGrid;
                const cache = suggestedSwitchCache.current;
                if (cache !== undefined) {
                    const points = tilePoints(olMap, suggestedSwitchesGrid.cellIndex);
                    const key = pointString(suggestedSwitchesGrid.cellIndex);
                    setIsLoadingSwitchSuggestion(true);
                    cache.cache
                        .getImmutable(key, () =>
                            requestLoadingSuggestedSwitch(
                                () =>
                                    getSuggestedSwitchesForLayoutSwitchPlacing(
                                        layoutContext.branch,
                                        points,
                                        linkingState.layoutSwitch.id,
                                    ),
                                () => suggestedSwitchCache.current === cache,
                            ),
                        )
                        .then((tileSwitches) => {
                            processLoadedTileSwitches(
                                cache,
                                suggestedSwitchesGrid,
                                tileSwitches,
                                hoveredLocation,
                            );
                        });
                }
            } else {
                positionInSuggestedSwitchGrid.current = undefined;
            }
        },
    };
}
