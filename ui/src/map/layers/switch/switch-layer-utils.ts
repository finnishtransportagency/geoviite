import { LayoutSwitch, LayoutSwitchJoint } from 'track-layout/track-layout-model';
import { State } from 'ol/render';
import { drawCircle, drawRoundedRect, getCanvasRenderer } from 'map/layers/utils/rendering';
import styles from '../../map.module.scss';
import Style, { RenderFunction } from 'ol/style/Style';
import SwitchIcon from 'vayla-design-lib/icon/glyphs/misc/switch.svg';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { SuggestedSwitchJoint } from 'linking/linking-model';
import { SwitchStructure } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import { Feature } from 'ol';
import { Point } from 'ol/geom';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { Circle, Fill, RegularShape, Stroke } from 'ol/style';
import mapStyles from 'map/map.module.scss';

const switchImage: HTMLImageElement = new Image();
switchImage.src = `data:image/svg+xml;utf8,${encodeURIComponent(SwitchIcon)}`;

const TEXT_FONT_LARGE = 12;
const TEXT_FONT_SMALL = 10;
const CIRCLE_RADIUS_SMALL = 4.5;
const CIRCLE_RADIUS_LARGE = 6.5;

export function getSelectedSwitchLabelRenderer(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    linked: boolean,
): RenderFunction {
    const paddingX = 6;
    const paddingY = 4;
    const iconTextPaddingX = 6;
    const fontSize = TEXT_FONT_LARGE;
    const labelOffset = (large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL) + 4;
    const iconSize = 14;
    const isGeometrySwitch = layoutSwitch.dataType == 'TEMP';

    return getCanvasRenderer(
        layoutSwitch,
        (ctx, { pixelRatio }) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = pixelRatio;
        },
        [
            ({ name }, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchLabel
                        : styles.unlinkedSwitchLabel
                    : styles.switchLabel;

                ctx.strokeStyle = styles.switchLabelBorder;
                const textSize = ctx.measureText(name);
                const height = (Math.max(iconSize, fontSize) + 2 * paddingY) * pixelRatio;
                const width =
                    textSize.width + (iconSize + 2 * paddingX + iconTextPaddingX) * pixelRatio;

                drawRoundedRect(
                    ctx,
                    x + labelOffset * pixelRatio,
                    y - height / 2,
                    width,
                    height,
                    2 * pixelRatio,
                );
            },
            (_, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = styles.switchTextColor;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                ctx.drawImage(
                    switchImage,
                    x + (labelOffset + paddingX) * pixelRatio,
                    y - (iconSize / 2) * pixelRatio,
                    iconSize * pixelRatio,
                    iconSize * pixelRatio,
                );
            },

            ({ name }, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = styles.switchTextColor;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                ctx.fillText(
                    name,
                    x + (labelOffset + iconSize + paddingX + iconTextPaddingX) * pixelRatio,
                    y + 2 * pixelRatio,
                );
            },
        ],
    );
}

export function getSwitchRenderer(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    showLabel: boolean,
    linked: boolean,
): RenderFunction {
    const fontSize = large ? TEXT_FONT_LARGE : TEXT_FONT_SMALL;
    const circleRadius = large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL;
    const textCirclePadding = 4;
    const isGeometrySwitch = layoutSwitch.dataType == 'TEMP';
    return getCanvasRenderer(
        layoutSwitch,
        (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = pixelRatio;
        },
        [
            (_, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchJoint
                        : styles.unlinkedSwitchJoint
                    : styles.switchJoint;
                ctx.strokeStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchJointBorder
                        : styles.unlinkedSwitchJointBorder
                    : styles.switchJointBorder;

                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },
            ({ name }, [x, y], ctx, { pixelRatio }) => {
                if (showLabel) {
                    ctx.fillStyle = styles.switchTextColor;
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'middle';

                    ctx.fillText(
                        name,
                        x + (circleRadius + textCirclePadding) * pixelRatio,
                        y + pixelRatio,
                    );
                }
            },
        ],
    );
}

export function getJointRenderer(joint: LayoutSwitchJoint, mainJoint: boolean): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const circleRadius = CIRCLE_RADIUS_LARGE;

    return getCanvasRenderer(
        joint,
        (ctx, { pixelRatio }) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = pixelRatio;
        },
        [
            (_, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = mainJoint ? styles.switchMainJoint : styles.switchJoint;
                ctx.strokeStyle = mainJoint
                    ? styles.switchMainJointBorder
                    : styles.switchJointBorder;

                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },

            ({ number }, [x, y], ctx) => {
                ctx.fillStyle = mainJoint
                    ? styles.switchMainJointTextColor
                    : styles.switchJointTextColor;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                ctx.fillText(switchJointNumberToString(number), x, y);
            },
        ],
    );
}

export function getLinkingJointRenderer(
    joint: SuggestedSwitchJoint,
    linked = false,
): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const circleRadius = CIRCLE_RADIUS_LARGE;
    const hasMatch = joint.matches.length > 0;

    return getCanvasRenderer(
        joint,
        (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = pixelRatio;
        },
        [
            (_, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = hasMatch
                    ? linked
                        ? styles.linkedSwitchJoint
                        : styles.unlinkedSwitchJoint
                    : styles.switchJoint;
                ctx.strokeStyle = hasMatch
                    ? linked
                        ? styles.linkedSwitchJointBorder
                        : styles.unlinkedSwitchJointBorder
                    : styles.switchJointBorder;

                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },

            ({ number }, [x, y], ctx) => {
                ctx.fillStyle = '#ffffff';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                ctx.fillText(switchJointNumberToString(number), x, y);
            },
        ],
    );
}

export function createFeatures(
    layoutSwitches: LayoutSwitch[],
    isSelected: (switchItem: LayoutSwitch) => boolean,
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
    isLinked: (switchItem: LayoutSwitch) => boolean,
    showLargeSymbols: boolean,
    showLabels: boolean,
    planId?: GeometryPlanId,
    switchStructures?: SwitchStructure[],
): Feature<Point>[] {
    return layoutSwitches
        .filter((s) => s.joints.length > 0)
        .flatMap((layoutSwitch) => {
            const selected = isSelected(layoutSwitch);
            const highlighted = isHighlighted(layoutSwitch);
            const linked = isLinked(layoutSwitch);
            const structure = switchStructures?.find(
                (structure) => structure.id === layoutSwitch.switchStructureId,
            );
            const presentationJointNumber = structure?.presentationJointNumber;

            return createSwitchFeatures(
                layoutSwitch,
                selected,
                highlighted,
                linked,
                showLargeSymbols,
                showLabels,
                planId,
                presentationJointNumber,
            );
        });
}

function createSwitchFeatures(
    layoutSwitch: LayoutSwitch,
    selected: boolean,
    highlighted: boolean,
    linked: boolean,
    largeSymbol: boolean,
    showLabel: boolean,
    planId?: GeometryPlanId,
    presentationJointNumber?: string | undefined,
): Feature<Point>[] {
    const presentationJoint = layoutSwitch.joints.find(
        (joint) => joint.number == presentationJointNumber,
    );

    // Use presentation joint as main joint if possible, otherwise use first joint
    const switchFeature = new Feature({
        geometry: new Point(
            pointToCoords(
                presentationJoint ? presentationJoint.location : layoutSwitch.joints[0].location,
            ),
        ),
    });

    switchFeature.setStyle(
        selected || highlighted
            ? selectedStyle(layoutSwitch, largeSymbol, linked)
            : unselectedStyle(layoutSwitch, largeSymbol, showLabel, linked),
    );

    switchFeature.set('switch-data', {
        switch: layoutSwitch,
        planId: planId,
    });

    const jointFeatures =
        selected || highlighted
            ? layoutSwitch.joints.map((joint, index) => {
                  const feature = new Feature({
                      geometry: new Point(pointToCoords(joint.location)),
                  });

                  feature.setStyle(
                      jointStyle(
                          joint,
                          // Again, use presentation joint as main joint if found, otherwise use first one
                          presentationJoint ? joint.number === presentationJointNumber : index == 0,
                      ),
                  );

                  feature.set('switch-data', {
                      switch: layoutSwitch,
                      joint: joint,
                      planId: planId,
                  });

                  return feature;
              })
            : [];

    return [switchFeature, ...jointFeatures];
}

function unselectedStyle(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    textLabel: boolean,
    linked: boolean,
): Style {
    return new Style({
        zIndex: 0,
        renderer: getSwitchRenderer(layoutSwitch, large, textLabel, linked),
    });
}

function selectedStyle(layoutSwitch: LayoutSwitch, large: boolean, linked: boolean): Style {
    return new Style({
        zIndex: 1,
        renderer: getSelectedSwitchLabelRenderer(layoutSwitch, large, linked),
    });
}

function jointStyle(joint: LayoutSwitchJoint, mainJoint: boolean): Style {
    return new Style({
        zIndex: mainJoint ? 3 : 2,
        renderer: getJointRenderer(joint, mainJoint),
    });
}

export const endPointStyle = [
    new Style({
        image: new Circle({
            radius: 6,
            fill: new Fill({ color: mapStyles.locationTrackEndPoint }),
            stroke: new Stroke({ color: mapStyles.locationTrackEndPointBorder }),
        }),
        zIndex: 4,
    }),
    new Style({
        image: new RegularShape({
            stroke: new Stroke({ color: mapStyles.locationTrackEndPointCross }),
            points: 4,
            radius: 4,
            radius2: 0,
            angle: 0,
        }),
        zIndex: 4,
    }),
];
