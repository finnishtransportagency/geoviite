import { describe, expect, test } from '@jest/globals';
import {
    BLOCK_SIZE,
    blockCells,
    blockKey,
    cellIndexInBlock,
    cellToBlock,
    cellToPoint,
    NEIGHBORHOOD_RADIUS,
    neighborhoodBlocks,
    pointToCell,
} from 'map/layers/switch/switch-placing-preview';

describe('switch placing preview blocks', () => {
    describe('cell/point mapping', () => {
        test('cellToPoint inverts pointToCell on lattice points', () => {
            const cell = { i: 12, j: -7 };
            expect(pointToCell(cellToPoint(cell, 10), 10)).toEqual(cell);
        });

        test('pointToCell rounds to the nearest lattice point', () => {
            expect(pointToCell({ x: 14, y: -16 }, 10)).toEqual({ i: 1, j: -2 });
        });
    });

    describe('block addressing', () => {
        test('blockCells lists exactly the cells addressed to the block, in index order', () => {
            [
                { i: 0, j: 0 },
                { i: 2, j: -3 },
                { i: -1, j: -1 },
            ].forEach((block) => {
                const cells = blockCells(block);
                expect(cells).toHaveLength(BLOCK_SIZE * BLOCK_SIZE);
                cells.forEach((cell, index) => {
                    expect(cellToBlock(cell)).toEqual(block);
                    expect(cellIndexInBlock(cell)).toBe(index);
                });
            });
        });

        test('adjacent cells across a block boundary map to different blocks', () => {
            expect(cellToBlock({ i: -1, j: 0 })).toEqual({ i: -1, j: 0 });
            expect(cellToBlock({ i: 0, j: 0 })).toEqual({ i: 0, j: 0 });
            expect(cellToBlock({ i: BLOCK_SIZE - 1, j: 0 })).toEqual({ i: 0, j: 0 });
            expect(cellToBlock({ i: BLOCK_SIZE, j: 0 })).toEqual({ i: 1, j: 0 });
        });
    });

    describe('neighborhoodBlocks', () => {
        test('covers every block holding a cell within NEIGHBORHOOD_RADIUS of the center', () => {
            const center = { i: 3, j: -8 };
            const keys = new Set(neighborhoodBlocks(center).map(blockKey));
            for (let di = -NEIGHBORHOOD_RADIUS; di <= NEIGHBORHOOD_RADIUS; di++) {
                for (let dj = -NEIGHBORHOOD_RADIUS; dj <= NEIGHBORHOOD_RADIUS; dj++) {
                    const cell = { i: center.i + di, j: center.j + dj };
                    expect(keys.has(blockKey(cellToBlock(cell)))).toBe(true);
                }
            }
        });

        test('returns a single block when the neighborhood fits inside one', () => {
            expect(neighborhoodBlocks({ i: NEIGHBORHOOD_RADIUS, j: NEIGHBORHOOD_RADIUS })).toEqual([
                { i: 0, j: 0 },
            ]);
        });

        test('returns four blocks around a block corner', () => {
            expect(neighborhoodBlocks({ i: 0, j: 0 })).toHaveLength(4);
        });
    });
});
