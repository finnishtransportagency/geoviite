import { MapTile } from 'map/map-model';
import OlView from 'ol/View';
import TileGrid from 'ol/tilegrid/TileGrid';
import { BoundingBox, Point } from 'model/geometry';

// offset used for defining a suitable boundingBox around a single location (Point)
export const MAP_POINT_DEFAULT_BBOX_OFFSET = 178;
export const MAP_POINT_CLOSEUP_BBOX_OFFSET = 78;

// Resolutions to use to load stuff from backend
const tileResolutions: number[] = [];
for (let i = 0; i < 22; i++) {
    tileResolutions[i] = 200000 / Math.pow(2, i);
}

export function calculateMapTiles(view: OlView, tileSizePx: number | undefined): MapTile[] {
    // Find a resolution that corresponds to the resolution in the view
    const actualResolution = view.getResolution() || tileResolutions[0];
    const tileResolutionIndex = tileResolutions.findIndex(
        (resolution, index) => resolution < actualResolution || index == tileResolutions.length - 1,
    );
    // Use OL tile grid to calc tiles
    const tileGrid = new TileGrid({
        origin: [0, 0], // any constant is OK
        resolutions: tileResolutions,
        tileSize: tileSizePx || calculateTileSize(3),
    });
    const tiles: MapTile[] = [];
    tileGrid.forEachTileCoord(view.calculateExtent(), tileResolutionIndex, function (tileCoord) {
        const tileExtent = tileGrid.getTileCoordExtent(tileCoord);
        const tile = {
            id: tileResolutionIndex + ':' + tileCoord.slice(1).join(':'),
            area: {
                x: { min: tileExtent[0], max: tileExtent[2] },
                y: { min: tileExtent[1], max: tileExtent[3] },
            },
            resolution: tileResolutions[tileResolutionIndex],
        };
        tiles.push(tile);
    });
    return tiles;
}

const tileSizeOptions = [256, 512, 1024, 2048, 4096];

/**
 * Picks a tile size where the given amount of tiles will fill the screen
 */
export function calculateTileSize(tileCount: number): number {
    const maxSize = tileSizeOptions[tileSizeOptions.length - 1];
    if (tileCount >= 1) {
        const optimalTileSize = Math.max(window.screen.width, window.screen.height) / tileCount;
        return tileSizeOptions.find((opt) => opt > optimalTileSize) || maxSize;
    } else {
        return maxSize;
    }
}

export function calculateBoundingBoxToShowAroundLocation(
    location: Point,
    offset: number = MAP_POINT_DEFAULT_BBOX_OFFSET,
): BoundingBox {
    return {
        x: {
            max: location.x + offset,
            min: location.x - offset,
        },
        y: {
            max: location.y + offset,
            min: location.y - offset,
        },
    };
}
