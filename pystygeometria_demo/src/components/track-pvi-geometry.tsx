import React from "react";
import {
  Coordinates,
  heightToY,
  mToX,
  polylinePoints,
} from "../math/coordinates";
import { formatRounded } from "../math/format";
import { profileHeightAtM, PviItem } from "../math/profile";
import {
  formatTrackKmPlusEvenMeters,
  parseTrackAddress,
} from "../math/track-address";

// Renders one location track's PVI geometry: the PVI assist line connecting the PVIs
// (drawn dimensions.pviAssistLineHeightPx above the actual heights), a diamond-topped
// arrow from the track profile up to each PVI, tangent arrows, and
// address/height/radius/slope labels. Ported from Geoviite's pvi-geometry.tsx; the
// coordinates passed in must be in this track's own m-space (see withTrackLocalM).

export interface TrackPviGeometryProps {
  items: PviItem[];
  coordinates: Coordinates;
  drawTangentArrows: boolean;
}

const minimumSpacePxForPviPointSideLabels = 14;
const minimumSpacePxForPviPointTopLabel = 16;
const minimumSpaceForTangentArrowLabel = 14;

const diamondHeightPx = 5;
const diamondWidthPx = 2;

function TangentArrow({
  left,
  pointStation,
  tangentStation,
  tangentHeight,
  tangent,
  coordinates,
}: {
  left: boolean;
  pointStation: number;
  tangentStation: number;
  tangentHeight: number;
  tangent: number;
  coordinates: Coordinates;
}): React.JSX.Element {
  const tangentX = mToX(coordinates, tangentStation);
  const tangentBottomPx = heightToY(coordinates, tangentHeight);
  const tangentTopPx =
    tangentBottomPx - coordinates.dimensions.pviAssistLineHeightPx / 1.5;

  const arrowWidth = 3;
  const arrowHeight = 3;

  const arrowPoints = polylinePoints([
    [tangentX - arrowWidth, tangentTopPx + arrowHeight],
    [tangentX, tangentTopPx],
    [tangentX + arrowWidth, tangentTopPx + arrowHeight],
  ]);

  const hasSpaceForText =
    coordinates.mMeterLengthPxOverM * Math.abs(tangentStation - pointStation) >
    (left
      ? minimumSpaceForTangentArrowLabel
      : minimumSpaceForTangentArrowLabel + minimumSpacePxForPviPointSideLabels);

  return (
    <React.Fragment>
      {hasSpaceForText && (
        <text
          className="diagram-text-stroke-narrow"
          transform={`translate(${tangentX + (left ? 10 : -4)},${
            tangentBottomPx - 2
          }) rotate(-90) scale(0.7)`}
        >
          {formatRounded(tangent, 2)}
        </text>
      )}
      <line
        x1={tangentX}
        x2={tangentX}
        y1={tangentBottomPx}
        y2={tangentTopPx}
        stroke="black"
        fill="none"
      />
      <polyline points={arrowPoints} stroke="black" fill="none" />
    </React.Fragment>
  );
}

const LeftMostStartingLine: React.FC<{
  coordinates: Coordinates;
  item: PviItem;
}> = ({ coordinates, item }) => {
  const { pviAssistLineHeightPx } = coordinates.dimensions;
  return (
    <line
      x1={mToX(coordinates, item.startM - item.backwardStraightLength)}
      x2={mToX(coordinates, item.pointM)}
      y1={
        heightToY(
          coordinates,
          item.startHeight - item.backwardStraightLength * item.startSlope,
        ) - pviAssistLineHeightPx
      }
      y2={heightToY(coordinates, item.pointHeight) - pviAssistLineHeightPx}
      stroke="black"
      fill="none"
    />
  );
};

const RightMostEndingLine: React.FC<{
  coordinates: Coordinates;
  item: PviItem;
}> = ({ coordinates, item }) => {
  const { pviAssistLineHeightPx } = coordinates.dimensions;
  return (
    <line
      stroke="black"
      x1={mToX(coordinates, item.pointM)}
      x2={mToX(coordinates, item.endM + item.forwardStraightLength)}
      y1={heightToY(coordinates, item.pointHeight) - pviAssistLineHeightPx}
      y2={
        heightToY(
          coordinates,
          item.endHeight + item.forwardStraightLength * item.endSlope,
        ) - pviAssistLineHeightPx
      }
    />
  );
};

const GeoLineDistanceAndAngleText: React.FC<{
  coordinates: Coordinates;
  geo: PviItem;
  nextGeo: PviItem;
}> = ({ coordinates, geo, nextGeo }) => {
  const approximateAngleTextLengthPx = 100;
  const angleTextScale = 0.7;
  const approximateAngleTextLengthM =
    (approximateAngleTextLengthPx / coordinates.mMeterLengthPxOverM) *
    angleTextScale;

  if (nextGeo.pointM - geo.pointM <= approximateAngleTextLengthM) {
    return <React.Fragment />;
  }

  const midpoint = geo.pointM + (nextGeo.pointM - geo.pointM) * 0.5;
  const angleTextStartM = midpoint - approximateAngleTextLengthM * 0.5;
  const angleTextStartProportion =
    (angleTextStartM - geo.pointM) / (nextGeo.pointM - geo.pointM);
  const angleTextStartHeight =
    (1 - angleTextStartProportion) * geo.pointHeight +
    angleTextStartProportion * nextGeo.pointHeight;
  const textStartX = mToX(coordinates, angleTextStartM);
  const textStartY =
    heightToY(coordinates, angleTextStartHeight) -
    coordinates.dimensions.pviAssistLineHeightPx -
    2;
  const angle =
    (-Math.atan2(
      coordinates.meterHeightPx * (nextGeo.pointHeight - geo.pointHeight),
      coordinates.mMeterLengthPxOverM * (nextGeo.pointM - geo.pointM),
    ) *
      180) /
    Math.PI;

  return (
    <text
      className="diagram-text-stroke-wide"
      transform={`translate(${textStartX},${textStartY}) rotate(${angle}) scale(${angleTextScale})`}
    >
      {formatRounded(nextGeo.pointM - geo.pointM, 3)}
      {" : "}
      {geo.endSlope !== 0 && formatRounded(geo.endSlope, 3)}
    </text>
  );
};

const GeoLine: React.FC<{
  coordinates: Coordinates;
  geo: PviItem;
  nextGeo: PviItem;
}> = ({ coordinates, geo, nextGeo }) => {
  const { pviAssistLineHeightPx } = coordinates.dimensions;
  return (
    <line
      x1={mToX(coordinates, geo.pointM)}
      x2={mToX(coordinates, nextGeo.pointM)}
      y1={heightToY(coordinates, geo.pointHeight) - pviAssistLineHeightPx}
      y2={heightToY(coordinates, nextGeo.pointHeight) - pviAssistLineHeightPx}
      stroke="black"
      fill="none"
    />
  );
};

const TangentArrows: React.FC<{
  geo: PviItem;
  coordinates: Coordinates;
  items: PviItem[];
}> = ({ geo, coordinates, items }) => (
  <React.Fragment>
    <TangentArrow
      left={true}
      pointStation={geo.pointM}
      tangentStation={geo.startM}
      tangentHeight={profileHeightAtM(items, geo.startM) ?? geo.startHeight}
      tangent={geo.tangent}
      coordinates={coordinates}
    />
    <TangentArrow
      left={false}
      pointStation={geo.pointM}
      tangentStation={geo.endM}
      tangentHeight={profileHeightAtM(items, geo.endM) ?? geo.endHeight}
      tangent={geo.tangent}
      coordinates={coordinates}
    />
  </React.Fragment>
);

const PointAddressText: React.FC<{ x: number; y: number; address: string }> = ({
  x,
  y,
  address,
}) => {
  const parsed = parseTrackAddress(address);
  return (
    <text
      className="diagram-text-stroke-wide"
      transform={`translate(${x},${y}) rotate(-90) scale(0.7)`}
    >
      KM {parsed ? formatTrackKmPlusEvenMeters(parsed) : address}
    </text>
  );
};

const PointHeightText: React.FC<{ x: number; y: number; height: number }> = ({
  height,
  x,
  y,
}) => (
  <text
    className="diagram-text-stroke-wide"
    transform={`translate(${x},${y}) rotate(-90) scale(0.7)`}
  >
    kt={formatRounded(height, 2)}
  </text>
);

const PointRadiusText: React.FC<{ x: number; y: number; radius: number }> = ({
  x,
  y,
  radius,
}) => (
  <text
    className="diagram-text-stroke-wide"
    transform={`translate(${x},${y}) rotate(-90) scale(0.6)`}
  >
    S={radius}
  </text>
);

function getMinimumSpaceAroundPoint(
  geo: PviItem,
  previousGeo: PviItem | undefined,
  nextGeo: PviItem | undefined,
  coordinates: Coordinates,
): number {
  const distances = [
    previousGeo ? geo.pointM - previousGeo.pointM : undefined,
    nextGeo ? nextGeo.pointM - geo.pointM : undefined,
  ].filter((distance): distance is number => distance !== undefined);
  return Math.min(...distances) * coordinates.mMeterLengthPxOverM;
}

const PviArrow: React.FC<{ x: number; bottomY: number; topY: number }> = ({
  x,
  bottomY,
  topY,
}) => (
  <>
    <line x1={x} x2={x} y1={bottomY} y2={topY} stroke="black" fill="none" />
    <polyline
      points={polylinePoints([
        [x, topY],
        [x - diamondWidthPx, topY - diamondHeightPx],
        [x, topY - 2 * diamondHeightPx],
        [x + diamondWidthPx, topY - diamondHeightPx],
        [x, topY],
      ])}
      fill="black"
      stroke="black"
    />
  </>
);

const PviPoint: React.FC<{
  pviPoint: PviItem;
  previousGeo: PviItem | undefined;
  nextGeo: PviItem | undefined;
  // The track's full item list, needed to evaluate the profile height under the point.
  items: PviItem[];
  coordinates: Coordinates;
  drawTangentArrows: boolean;
}> = ({
  pviPoint,
  previousGeo,
  nextGeo,
  items,
  coordinates,
  drawTangentArrows,
}) => {
  const x = mToX(coordinates, pviPoint.pointM);
  // bottomY is on the actual track profile, topY is at the PVI assist line
  const bottomHeight =
    profileHeightAtM(items, pviPoint.pointM) ?? pviPoint.pointHeight;
  const bottomY = heightToY(coordinates, bottomHeight);
  const topY =
    heightToY(coordinates, pviPoint.pointHeight) -
    coordinates.dimensions.pviAssistLineHeightPx;

  const minimumSpaceAroundPointPx = getMinimumSpaceAroundPoint(
    pviPoint,
    previousGeo,
    nextGeo,
    coordinates,
  );

  return (
    <>
      {nextGeo && (
        <>
          <GeoLineDistanceAndAngleText
            coordinates={coordinates}
            geo={pviPoint}
            nextGeo={nextGeo}
          />
          <GeoLine coordinates={coordinates} geo={pviPoint} nextGeo={nextGeo} />
        </>
      )}
      {drawTangentArrows && (
        <TangentArrows geo={pviPoint} coordinates={coordinates} items={items} />
      )}
      {minimumSpaceAroundPointPx > minimumSpacePxForPviPointSideLabels && (
        <>
          <PointAddressText
            x={x - 8}
            y={topY - 4}
            address={pviPoint.pointAddress}
          />
          <PointHeightText
            x={x + 10}
            y={bottomY - 4}
            height={pviPoint.pointHeight}
          />
        </>
      )}
      <PviArrow x={x} bottomY={bottomY} topY={topY} />
      {minimumSpaceAroundPointPx > minimumSpacePxForPviPointTopLabel && (
        <PointRadiusText
          x={x + diamondWidthPx}
          y={topY - diamondHeightPx - 10}
          radius={pviPoint.radius}
        />
      )}
    </>
  );
};

export const TrackPviGeometry: React.FC<TrackPviGeometryProps> = ({
  items,
  coordinates,
  drawTangentArrows,
}) => {
  const firstItem = items[0];
  const lastItem = items[items.length - 1];
  if (!firstItem || !lastItem) {
    return <React.Fragment />;
  }

  // Render the PVIs intersecting the view plus one extra on each side, since a PVI's
  // lines extend to its neighbours. findIndex returning -1 means the view is past the
  // last PVI; only the last one can still reach into the view then.
  const firstPviInView = items.findIndex((s) => s.endM >= coordinates.startM);
  const leftPviI =
    firstPviInView === -1 ? items.length - 1 : Math.max(firstPviInView - 1, 0);
  const firstPviPastView = items.findIndex((s) => s.startM >= coordinates.endM);
  const rightPviI =
    firstPviPastView === -1 ? items.length - 1 : firstPviPastView;

  return (
    <>
      {leftPviI === 0 && (
        <LeftMostStartingLine coordinates={coordinates} item={firstItem} />
      )}
      {rightPviI === items.length - 1 && (
        <RightMostEndingLine coordinates={coordinates} item={lastItem} />
      )}
      {items.slice(leftPviI, rightPviI + 1).map((item, offset) => {
        const index = leftPviI + offset;
        return (
          <PviPoint
            key={item.id}
            pviPoint={item}
            previousGeo={items[index - 1]}
            nextGeo={items[index + 1]}
            items={items}
            coordinates={coordinates}
            drawTangentArrows={drawTangentArrows}
          />
        );
      })}
    </>
  );
};
