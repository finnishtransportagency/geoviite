// Interval of 25 pointedly left out to have nicely placed divisors in the sequence. If we had an interval of 25, we'd
// too easily end up in a situation where we're displaying ticks every 5 meters and labels every 25 meters, but then
// when we zoom in a bit, the ticks move to being every 2 meters; so the labels at xx25 and xx75 meters disappear!

const labelIntervalOptions = [1, 2, 5, 10, 50, 100, 250, 500, 1000] as const;

export const minimumApproximateHorizontalTickWidthPx = 15;
export const minimumLabeledTickDistancePx = 120;

// must be <=minimumApproximateHorizontalTickWidthPx, or we'll sometimes hide ticks when zooming in
export const minimumRulerHeightLabelDistancePx = 15;

export function minimumInterval(itemWidth: number, minimumWidth: number): number | undefined {
    return labelIntervalOptions.find((interval) => itemWidth * interval >= minimumWidth);
}

export function minimumIntervalOrLongest(itemWidth: number, minimumWidth: number): number {
    return minimumInterval(itemWidth, minimumWidth) ?? labelIntervalOptions[8];
}
