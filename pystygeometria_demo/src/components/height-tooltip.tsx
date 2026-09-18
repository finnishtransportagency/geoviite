import React from "react";
import { SnappedPoint } from "../math/snapped-point";
import { formatRounded } from "../math/format";
import { formatTrackAddress } from "../math/track-address";
import { DiagramDimensions } from "../math/coordinates";

// Tooltip beside the snapped point, positioned absolutely within the diagram div (which
// is position: relative) and nudged left/up as needed to stay inside the diagram.

interface HeightTooltipProps {
  point: SnappedPoint;
  dimensions: DiagramDimensions;
}

export const HeightTooltip: React.FC<HeightTooltipProps> = ({
  point,
  dimensions,
}) => {
  const ref = React.useRef<HTMLDivElement>(null);
  const [tooltipSize, setTooltipSize] = React.useState({ w: 0, h: 0 });

  React.useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const { offsetWidth: w, offsetHeight: h } = el;
    setTooltipSize((prev) => (prev.w === w && prev.h === h ? prev : { w, h }));
  });

  return (
    <div
      ref={ref}
      className="height-tooltip"
      style={{
        position: "absolute",
        left: Math.min(
          point.xPositionPx + 20,
          dimensions.widthPx - tooltipSize.w,
        ),
        top: Math.min(
          point.yPositionPx + 20,
          dimensions.heightPx - tooltipSize.h,
        ),
        pointerEvents: "none",
      }}
    >
      {formatTrackAddress(point.address)}
      <br />
      kt={formatRounded(point.height, 2)}
    </div>
  );
};
