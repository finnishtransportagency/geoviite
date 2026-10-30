import { describe, expect, it, jest } from '@jest/globals';

jest.mock('ol/View', () => ({ __esModule: true, default: class OlView {} }));
jest.mock('ol/tilegrid/TileGrid', () => ({ __esModule: true, default: class TileGrid {} }));

import {
    calculateBoundingBoxToShowAroundLocation,
    calculateOperationalPointBoundingBox,
    calculateSwitchBoundingBox,
} from 'map/map-utils';

describe('calculateBoundingBoxToShowAroundLocation', () => {
    it('uses the detailed focus area around a location', () => {
        expect(calculateBoundingBoxToShowAroundLocation({ x: 100, y: 200 })).toEqual({
            x: { min: -78, max: 278 },
            y: { min: 22, max: 378 },
        });
    });
});

describe('calculateOperationalPointBoundingBox', () => {
    it('includes both the operational point location margin and its full polygon area', () => {
        expect(
            calculateOperationalPointBoundingBox(
                { x: 100, y: 200 },
                {
                    points: [
                        { x: -800, y: -200 },
                        { x: 300, y: -200 },
                        { x: 300, y: 950 },
                        { x: -800, y: 950 },
                        { x: -800, y: -200 },
                    ],
                },
            ),
        ).toEqual({
            x: { min: -800, max: 600 },
            y: { min: -300, max: 950 },
        });
    });

    it('uses the location margin when the polygon is missing', () => {
        expect(calculateOperationalPointBoundingBox({ x: 100, y: 200 })).toEqual({
            x: { min: -400, max: 600 },
            y: { min: -300, max: 700 },
        });
    });

    it('uses the polygon area when the point location is missing', () => {
        expect(
            calculateOperationalPointBoundingBox(undefined, {
                points: [
                    { x: -20, y: -10 },
                    { x: 30, y: -10 },
                    { x: 30, y: 40 },
                    { x: -20, y: 40 },
                    { x: -20, y: -10 },
                ],
            }),
        ).toEqual({
            x: { min: -20, max: 30 },
            y: { min: -10, max: 40 },
        });
    });
});

describe('calculateSwitchBoundingBox', () => {
    it('centers the focus box on the bounds of all switch joints', () => {
        expect(
            calculateSwitchBoundingBox(undefined, [
                { x: 100, y: 200 },
                { x: 300, y: 600 },
            ]),
        ).toEqual({
            x: { min: 22, max: 378 },
            y: { min: 222, max: 578 },
        });
    });

    it('returns no focus box when the switch has no joints', () => {
        expect(calculateSwitchBoundingBox(undefined, [])).toBeUndefined();
    });

    it('uses the switch location when it has no joints', () => {
        expect(calculateSwitchBoundingBox({ x: 100, y: 200 }, [])).toEqual({
            x: { min: -78, max: 278 },
            y: { min: 22, max: 378 },
        });
    });
});
