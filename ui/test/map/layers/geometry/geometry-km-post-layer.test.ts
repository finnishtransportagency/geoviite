import { afterEach, describe, expect, jest, test } from '@jest/globals';
import { getVisiblePlansWithStatus } from 'map/layers/utils/layer-utils';
import { mainOfficialLayoutContext } from 'common/common-model';
import { brand } from 'common/brand';
import { initialChangeTimes } from 'common/common-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { Selection, VisiblePlanLayout } from 'selection/selection-model';
import { createGeometryKmPostLayer } from 'map/layers/geometry/geometry-km-post-layer';

jest.mock('map/layers/utils/layer-utils', () => ({
    createLayer: jest.fn(() => ({ layer: {}, source: {}, isLatest: () => true })),
    getManualPlanWithStatus: jest.fn(),
    getVisiblePlansWithStatus: jest.fn(() => Promise.resolve([])),
    loadLayerData: jest.fn(),
}));
jest.mock('map/layers/utils/km-post-layer-utils', () => ({
    createKmPostFeatures: jest.fn(() => []),
    findMatchingKmPosts: jest.fn(() => []),
    getKmPostStepByResolution: jest.fn(() => 0),
}));

afterEach(() => {
    jest.clearAllMocks();
});

describe('geometry km post layer data loading', () => {
    test('skips fetching plans when no km posts are selected at far zoom', () => {
        const selection = createSelection([
            createVisiblePlan('plan-a'),
            createVisiblePlan('plan-b'),
        ]);

        createLayer(selection);

        expect(getVisiblePlansWithStatus).not.toHaveBeenCalled();
    });

    test('fetches only plans containing selected km posts at far zoom', () => {
        const planA = createVisiblePlan('plan-a');
        const planB = createVisiblePlan('plan-b');
        const selection = createSelection([planA, planB]);
        selection.selectedItems.geometryKmPostIds.push({
            planId: planA.id,
            geometryId: brand<'GeometryKmPostId'>(`${planA.id}-km-post`),
        });

        createLayer(selection);

        expect(getVisiblePlansWithStatus).toHaveBeenCalledWith(
            [planA],
            [],
            mainOfficialLayoutContext,
            initialChangeTimes,
        );
    });
});

function createLayer(selection: Selection) {
    createGeometryKmPostLayer(
        [],
        2000,
        undefined,
        selection,
        mainOfficialLayoutContext,
        initialChangeTimes,
        undefined,
        jest.fn(),
    );
}

function createSelection(visiblePlans: VisiblePlanLayout[]): Selection {
    return {
        selectedItems: createEmptyItemCollections(),
        highlightedItems: createEmptyItemCollections(),
        openPlans: [],
        visiblePlans,
        publicationId: undefined,
    };
}

function createVisiblePlan(id: string): VisiblePlanLayout {
    return {
        id,
        switches: [],
        kmPosts: [brand<'GeometryKmPostId'>(`${id}-km-post`)],
        alignments: [],
    };
}
