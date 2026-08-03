import { Point } from 'model/geometry';
import { SuggestedSwitch } from 'linking/linking-model';
import { brand, Brand } from 'common/brand';

// Spacing between adjacent grid points, in screen pixels. Multiplied by the current map resolution
// to get the spacing in map units, so the preview gets finer as the user zooms in to place precisely.
export const GRID_STEP_PX = 1;
// Radius, in cells, of the neighborhood kept fetched around the cursor's cell.
export const NEIGHBORHOOD_RADIUS = 6;
// Cells per side of a block, the unit of fetching and caching. Equal to the diameter of the
// cursor's neighborhood, so the neighborhood overlaps at most four blocks.
export const BLOCK_SIZE = 2 * NEIGHBORHOOD_RADIUS + 1;

// Integer coordinates of a cell on the sampling lattice. The lattice is anchored at the map origin
// — cell (i, j) sits at (i * step, j * step) in map coordinates — rather than at any particular
// cursor position, so every fetch made at the same step size samples the same locations and its
// results can be shared by all cursor positions.
export type GridCell = { i: number; j: number };

// Integer coordinates of a block on the coarser BLOCK_SIZE-spaced lattice of fetch units.
export type GridBlock = { i: number; j: number };

export type SwitchPlacingBlockKey = Brand<string, 'SwitchPlacingBlockKey'>;

// The fetched result for one block: a suggestion (or none) for each of the BLOCK_SIZE² lattice
// cells it covers, indexed by cellIndexInBlock.
export type BlockSuggestions = (SuggestedSwitch | undefined)[];

export function blockKey(block: GridBlock): SwitchPlacingBlockKey {
    return brand(`${block.i},${block.j}`);
}

export function pointToCell(point: Point, step: number): GridCell {
    return { i: Math.round(point.x / step), j: Math.round(point.y / step) };
}

export function cellToPoint(cell: GridCell, step: number): Point {
    return { x: cell.i * step, y: cell.j * step };
}

export function cellToBlock(cell: GridCell): GridBlock {
    return { i: Math.floor(cell.i / BLOCK_SIZE), j: Math.floor(cell.j / BLOCK_SIZE) };
}

// Remainder that stays non-negative for negative cell coordinates.
const mod = (n: number, m: number): number => ((n % m) + m) % m;

// Index of a lattice cell within its block's BlockSuggestions array.
export function cellIndexInBlock(cell: GridCell): number {
    return mod(cell.j, BLOCK_SIZE) * BLOCK_SIZE + mod(cell.i, BLOCK_SIZE);
}

// The lattice cells covered by a block, in BlockSuggestions array order; what a block fetch
// queries.
export function blockCells(block: GridBlock): GridCell[] {
    const cells: GridCell[] = [];
    for (let j = block.j * BLOCK_SIZE; j < (block.j + 1) * BLOCK_SIZE; j++) {
        for (let i = block.i * BLOCK_SIZE; i < (block.i + 1) * BLOCK_SIZE; i++) {
            cells.push({ i, j });
        }
    }
    return cells;
}

// The blocks overlapping the Chebyshev-NEIGHBORHOOD_RADIUS neighborhood of the given lattice cell:
// what should be fetched (at most four blocks) to cover the area around the cursor.
export function neighborhoodBlocks(center: GridCell): GridBlock[] {
    const min = cellToBlock({
        i: center.i - NEIGHBORHOOD_RADIUS,
        j: center.j - NEIGHBORHOOD_RADIUS,
    });
    const max = cellToBlock({
        i: center.i + NEIGHBORHOOD_RADIUS,
        j: center.j + NEIGHBORHOOD_RADIUS,
    });
    const blocks: GridBlock[] = [];
    for (let j = min.j; j <= max.j; j++) {
        for (let i = min.i; i <= max.i; i++) {
            blocks.push({ i, j });
        }
    }
    return blocks;
}
