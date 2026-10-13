import React from "react";
import {
  defaultDiagramDimensions,
  DiagramDimensions,
  heightBoundsOf,
  makeCoordinates,
  mToX,
  withTrackLocalM,
} from "../math/coordinates";
import { Layout, pannedView, ViewRange, zoomedView } from "../math/layout";
import { collectVisibleHeights, DiagramTrack } from "../math/diagram-model";
import { getSnappedPoint } from "../math/snapped-point";
import { TrackPviGeometry } from "./track-pvi-geometry";
import { HeightGraph } from "./height-graph";
import { HeightLabels, HeightLines } from "./height-lines";
import { HeightTooltip } from "./height-tooltip";
import { OperationalPointMarkers } from "./operational-point-markers";
import { PlacedOperationalPoint } from "../math/operational-points";

const minimumPixelWidthToDrawTangentArrows = 0.05;

export interface DiagramProps {
  tracks: DiagramTrack[];
  layout: Layout;
  view: ViewRange;
  operationalPoints: PlacedOperationalPoint[];
  onViewChange: (view: ViewRange) => void;
  // Sizes and positions shaping the diagram; anything not given falls back to
  // defaultDiagramDimensions. Pass these to fit the diagram into an embedding site.
  dimensions?: Partial<DiagramDimensions>;
}

export const Diagram: React.FC<DiagramProps> = ({
  tracks,
  layout,
  view,
  operationalPoints,
  onViewChange,
  dimensions,
}) => {
  const ref = React.useRef<HTMLDivElement>(null);
  const clipIdPrefix = React.useId();
  const [panning, setPanning] = React.useState<number>();
  const [mousePosition, setMousePosition] = React.useState<
    [number, number] | undefined
  >();

  const fullDimensions = React.useMemo(
    () => ({ ...defaultDiagramDimensions, ...dimensions }),
    [dimensions],
  );
  const coordinates = React.useMemo(() => {
    const heightBounds = heightBoundsOf(
      collectVisibleHeights(tracks, layout, view),
    );
    return makeCoordinates(
      fullDimensions,
      view.startX,
      view.endX,
      heightBounds,
    );
  }, [fullDimensions, tracks, layout, view]);

  const drawTangentArrows =
    coordinates.mMeterLengthPxOverM > minimumPixelWidthToDrawTangentArrows;

  // The wheel listener must be non-passive to be able to preventDefault page scroll,
  // so it is attached manually instead of through an onWheel prop.
  React.useEffect(() => {
    const element = ref.current;
    if (!element) {
      return undefined;
    }
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const elementLeft = element.getBoundingClientRect().x;
      const focusX =
        (e.clientX - elementLeft) / coordinates.mMeterLengthPxOverM +
        view.startX;
      onViewChange(zoomedView(view, layout.totalLength, focusX, e.deltaY));
    };
    element.addEventListener("wheel", onWheel, { passive: false });
    return () => element.removeEventListener("wheel", onWheel);
  }, [view, layout, coordinates, onViewChange]);

  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (panning !== undefined) {
      const deltaXM = (panning - e.clientX) / coordinates.mMeterLengthPxOverM;
      onViewChange(pannedView(view, layout.totalLength, deltaXM));
      setPanning(e.clientX);
    }
    const rect = e.currentTarget.getBoundingClientRect();
    setMousePosition([e.clientX - rect.left, e.clientY - rect.top]);
  };

  const snappedPoint =
    panning === undefined
      ? getSnappedPoint(
          mousePosition,
          tracks,
          layout,
          coordinates,
          drawTangentArrows,
        )
      : undefined;

  return (
    <div
      ref={ref}
      className={`diagram ${panning !== undefined ? "diagram--panning" : ""}`}
      style={{ width: fullDimensions.widthPx, height: fullDimensions.heightPx }}
      onPointerDown={(e) => {
        e.preventDefault();
        e.currentTarget.setPointerCapture(e.pointerId);
        setPanning(e.clientX);
      }}
      onPointerUp={(e) => {
        e.currentTarget.releasePointerCapture(e.pointerId);
        setPanning(undefined);
      }}
      onPointerLeave={() => {
        setPanning(undefined);
        setMousePosition(undefined);
      }}
      onPointerMove={onPointerMove}
    >
      <svg width={fullDimensions.widthPx} height={fullDimensions.heightPx}>
        <HeightLines coordinates={coordinates} />
        {layout.spans.map((span, spanIndex) => {
          const track = tracks[spanIndex];
          if (
            !track ||
            span.offsetX + span.lengthM < view.startX ||
            span.offsetX > view.endX
          ) {
            return undefined;
          }
          const trackCoordinates = withTrackLocalM(
            coordinates,
            span.offsetX,
            span.startM,
          );
          const startXPx = mToX(coordinates, span.offsetX);
          const endXPx = mToX(coordinates, span.offsetX + span.lengthM);
          const clipId = `${clipIdPrefix}${spanIndex}`;
          return (
            <g key={span.key}>
              {spanIndex > 0 && (
                <line
                  x1={startXPx}
                  x2={startXPx}
                  y1={0}
                  y2={fullDimensions.heightPx}
                  stroke="black"
                  strokeDasharray="2 6"
                />
              )}
              <text
                className="diagram-text-stroke-wide"
                x={Math.max(startXPx, 0) + 6}
                y={14}
              >
                {span.name}
              </text>
              {/* A route span's PVI items cover its whole track and can reach past the
                  span's ends; clip the drawing to the span's own horizontal extent. */}
              <clipPath id={clipId}>
                <rect
                  x={startXPx}
                  width={Math.max(endXPx - startXPx, 0)}
                  y={0}
                  height={fullDimensions.heightPx}
                />
              </clipPath>
              <g clipPath={`url(#${clipId})`}>
                <HeightGraph
                  items={track.items}
                  coordinates={trackCoordinates}
                  trackStartM={span.startM}
                  trackEndM={span.startM + span.lengthM}
                  prevItem={track.prevItem}
                  nextItem={track.nextItem}
                />
                <TrackPviGeometry
                  items={track.items}
                  coordinates={trackCoordinates}
                  drawTangentArrows={drawTangentArrows}
                />
              </g>
            </g>
          );
        })}
        <OperationalPointMarkers
          operationalPoints={operationalPoints}
          layout={layout}
          coordinates={coordinates}
        />
        <HeightLabels coordinates={coordinates} />
        {snappedPoint && (
          <circle
            cx={snappedPoint.xPositionPx}
            cy={snappedPoint.yPositionPx}
            r={4}
            fill="red"
            stroke="white"
            strokeWidth={1.5}
            pointerEvents="none"
          />
        )}
      </svg>
      {snappedPoint && (
        <HeightTooltip point={snappedPoint} dimensions={fullDimensions} />
      )}
    </div>
  );
};
