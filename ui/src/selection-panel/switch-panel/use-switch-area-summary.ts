import { LayoutContext } from 'common/common-model';
import { BoundingBox } from 'model/geometry';
import { getSwitchesAreaSummary, SwitchAreaSummary } from 'track-layout/layout-switch-api';
import { debounceAsync } from 'utils/async-utils';
import { useLoader } from 'utils/react-utils';

const VIEWPORT_DEBOUNCE_MS = 300;

const debouncedGetSwitchesAreaSummary = debounceAsync(getSwitchesAreaSummary, VIEWPORT_DEBOUNCE_MS);

export function useSwitchAreaSummary(
    area: BoundingBox | undefined,
    maxSwitches: number,
    layoutContext: LayoutContext,
): SwitchAreaSummary | undefined {
    return useLoader(() => {
        return area
            ? debouncedGetSwitchesAreaSummary(
                  area,
                  layoutContext,
                  maxSwitches,
                  false, // includeSwitchesWithNoJoints
              )
            : Promise.resolve(undefined);
    }, [area, maxSwitches, layoutContext]);
}
