import { describe, expect, test } from '@jest/globals';
import { shouldShowKmPostAtStep } from 'map/layers/utils/km-post-layer-visibility';

describe('shouldShowKmPostAtStep', () => {
    test('shows a selected km post when sampling is disabled', () => {
        expect(shouldShowKmPostAtStep('12', 0, true)).toBe(true);
    });

    test('hides an unselected km post when sampling is disabled', () => {
        expect(shouldShowKmPostAtStep('12', 0, false)).toBe(false);
    });

    test('shows selected km posts even when they do not match the sampling step', () => {
        expect(shouldShowKmPostAtStep('12', 5, true)).toBe(true);
    });

    test('samples unselected km posts by step', () => {
        expect(shouldShowKmPostAtStep('10', 5, false)).toBe(true);
        expect(shouldShowKmPostAtStep('12', 5, false)).toBe(false);
    });
});
