import { LayoutSwitch, LayoutSwitchJoint } from 'track-layout/track-layout-model';
import { State } from 'ol/render';
import {
    drawCircle,
    drawRect,
    drawRoundedRect,
    getCanvasRenderer,
} from 'map/layers/utils/rendering';
import styles from '../../map.module.scss';
import Style, { RenderFunction } from 'ol/style/Style';
import SwitchIcon from 'vayla-design-lib/icon/glyphs/misc/switch.svg';
import SwitchErrorIcon from 'vayla-design-lib/icon/glyphs/misc/switch-error.svg';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { SuggestedSwitchJoint } from 'linking/linking-model';
import { SwitchStructure } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import VectorSource from 'ol/source/Vector';
import { Rectangle } from 'model/geometry';
import { ValidatedAsset } from 'publication/publication-model';

const switchImage: HTMLImageElement = new Image();
switchImage.src = `data:image/svg+xml;utf8,${encodeURIComponent(SwitchIcon)}`;

const switchErrorImage: HTMLImageElement = new Image();
switchErrorImage.src = `data:image/svg+xml;utf8,${encodeURIComponent(SwitchErrorIcon)}`;

const TEXT_FONT_LARGE = 11;
const TEXT_FONT_SMALL = 10;
const CIRCLE_RADIUS_SMALL = 4.5;
const CIRCLE_RADIUS_LARGE = 6.5;

export function getSelectedSwitchLabelRenderer(
    layoutSwitch: LayoutSwitch,
    linked: boolean,
    isValid: boolean,
): RenderFunction {
    const paddingX = 6;
    const paddingY = 4;
    const iconTextPaddingX = 6;
    const fontSize = TEXT_FONT_LARGE;
    const strokeWidth = isValid ? 1 : 2;
    const labelOffset = CIRCLE_RADIUS_SMALL + 4 + strokeWidth * 2;
    const iconSize = 14;
    const isGeometrySwitch = layoutSwitch.dataType == 'TEMP';
    return getCanvasRenderer(
        layoutSwitch,
        (ctx, { pixelRatio }) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = strokeWidth * pixelRatio;
        },
        [
            ({ name }, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchLabel
                        : styles.unlinkedSwitchLabel
                    : styles.switchLabel;

                ctx.strokeStyle = isValid ? styles.switchLabelBorder : styles.errorBright;
                const textSize = ctx.measureText(name);
                const height = (Math.max(iconSize, fontSize) + 2 * paddingY) * pixelRatio;
                const width =
                    textSize.width +
                    (iconSize + 2 * paddingX + iconTextPaddingX + strokeWidth / 2) * pixelRatio;

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
                ctx.fillStyle = isValid ? styles.switchTextColor : styles.errorDefault;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                ctx.drawImage(
                    isValid ? switchImage : switchErrorImage,
                    x + (labelOffset + paddingX) * pixelRatio,
                    y - (iconSize / 2) * pixelRatio,
                    iconSize * pixelRatio,
                    iconSize * pixelRatio,
                );
            },

            ({ name }, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = styles.switchTextColor;
                ctx.fillStyle = isValid ? styles.switchTextColor : styles.errorDefault;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                ctx.fillText(
                    name,
                    x + (labelOffset + iconSize + paddingX + iconTextPaddingX) * pixelRatio,
                    y + 2 * pixelRatio - strokeWidth / 2,
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
    isValid: boolean,
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
                    : isValid
                    ? styles.switchJointBorder
                    : styles.errorBright;
                ctx.lineWidth = (isValid ? 1 : 3) * pixelRatio;

                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },
            ({ name }, [x, y], ctx, { pixelRatio }) => {
                if (showLabel) {
                    ctx.fillStyle = styles.switchBackground;
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'middle';

                    const textWidth = ctx.measureText(name).width;
                    const textX = x + (circleRadius + textCirclePadding) * pixelRatio;
                    const textY = y + pixelRatio;
                    const paddingHor = 2;
                    const paddingVer = 1;
                    const contentWidth = textWidth + (isValid ? 0 : 15);
                    const backgroundX = textX - paddingHor * pixelRatio - pixelRatio;
                    const backgroundY =
                        textY - (fontSize * pixelRatio) / 2 - paddingVer * pixelRatio;
                    const backgroundWidth = contentWidth + paddingHor * 2 * pixelRatio;
                    const backgroundHeight = fontSize * pixelRatio + paddingVer * 2 * pixelRatio;

                    drawRect(ctx, backgroundX, backgroundY, backgroundWidth, backgroundHeight);

                    ctx.fillStyle = styles.switchTextColor;
                    ctx.fillText(name, textX, textY);
                }
            },
        ],
    );
}

export function getJointRenderer(
    joint: LayoutSwitchJoint,
    mainJoint: boolean,
    isValid: boolean = true,
): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const showErrorStyle = !isValid && mainJoint;
    const circleRadius = showErrorStyle ? CIRCLE_RADIUS_LARGE + 1 : CIRCLE_RADIUS_LARGE;

    return getCanvasRenderer(
        joint,
        (ctx, { pixelRatio }) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = (!isValid && mainJoint ? 3 : 1) * pixelRatio;
        },
        [
            (_, [x, y], ctx, { pixelRatio }) => {
                ctx.fillStyle = mainJoint ? styles.switchMainJoint : styles.switchJoint;
                ctx.strokeStyle = showErrorStyle
                    ? styles.errorBright
                    : mainJoint
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

export function createSwitchFeatures(
    layoutSwitches: LayoutSwitch[],
    isSelected: (switchItem: LayoutSwitch) => boolean,
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
    isLinked: (switchItem: LayoutSwitch) => boolean,
    showLargeSymbols: boolean,
    showLabels: boolean,
    planId?: GeometryPlanId,
    switchStructures?: SwitchStructure[],
    validationResult?: ValidatedAsset[],
): Feature<OlPoint>[] {
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

            return createSwitchFeature(
                layoutSwitch,
                selected,
                highlighted,
                linked,
                showLargeSymbols,
                showLabels,
                planId,
                presentationJointNumber,
                validationResult?.find((sw) => sw.id === layoutSwitch.id),
            );
        });
}

function createSwitchFeature(
    layoutSwitch: LayoutSwitch,
    selected: boolean,
    highlighted: boolean,
    linked: boolean,
    largeSymbol: boolean,
    showLabel: boolean,
    planId?: GeometryPlanId,
    presentationJointNumber?: string | undefined,
    validationResult?: ValidatedAsset | undefined,
): Feature<OlPoint>[] {
    const presentationJoint = layoutSwitch.joints.find(
        (joint) => joint.number == presentationJointNumber,
    );

    // Use presentation joint as main joint if possible, otherwise use first joint
    const switchFeature = new Feature({
        geometry: new OlPoint(
            pointToCoords(presentationJoint?.location ?? layoutSwitch.joints[0].location),
        ),
    });

    switchFeature.setStyle(
        selected || highlighted
            ? getSelectedSwitchStyle(layoutSwitch, linked, true)
            : getUnselectedSwitchStyle(layoutSwitch, largeSymbol, showLabel, linked, true),
    );

    if (validationResult?.errors?.length) {
        switchFeature.setStyle(
            selected || highlighted
                ? getSelectedSwitchStyle(layoutSwitch, linked, false)
                : getUnselectedSwitchStyle(layoutSwitch, largeSymbol, showLabel, linked, false),
        );
    }

    setSwitchFeatureProperty(switchFeature, { switch: layoutSwitch, planId: planId });

    const jointFeatures =
        selected || highlighted
            ? layoutSwitch.joints.map((joint, index) => {
                  const feature = new Feature({
                      geometry: new OlPoint(pointToCoords(joint.location)),
                  });

                  feature.setStyle(
                      getSwitchJointStyle(
                          joint,
                          // Again, use presentation joint as main joint if found, otherwise use first one
                          presentationJoint ? joint.number === presentationJointNumber : index == 0,
                      ),
                  );

                  if (validationResult?.errors?.length) {
                      feature.setStyle(
                          getSwitchJointStyle(
                              joint,
                              // Again, use presentation joint as main joint if found, otherwise use first one
                              presentationJoint
                                  ? joint.number === presentationJointNumber
                                  : index == 0,
                              false,
                          ),
                      );
                  }

                  return feature;
              })
            : [];

    return [switchFeature, ...jointFeatures];
}

function getUnselectedSwitchStyle(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    textLabel: boolean,
    linked: boolean,
    isValid: boolean,
): Style {
    return new Style({
        zIndex: 0,
        renderer: getSwitchRenderer(layoutSwitch, large, textLabel, linked, isValid),
    });
}

function getSelectedSwitchStyle(
    layoutSwitch: LayoutSwitch,
    linked: boolean,
    isValid: boolean = true,
): Style {
    return new Style({
        zIndex: 1,
        renderer: getSelectedSwitchLabelRenderer(layoutSwitch, linked, isValid),
    });
}

function getSwitchJointStyle(
    joint: LayoutSwitchJoint,
    mainJoint: boolean,
    isValid: boolean = true,
): Style {
    return new Style({
        zIndex: mainJoint ? 3 : 2,
        renderer: getJointRenderer(joint, mainJoint, isValid),
    });
}

type SwitchFeatureProperty = {
    switch: LayoutSwitch;
    planId: GeometryPlanId | undefined;
};

const SWITCH_FEATURE_DATA_PROPERTY = 'switch-data';

export function findMatchingSwitches(
    hitArea: Rectangle,
    source: VectorSource,
    options: SearchItemsOptions,
) {
    return findMatchingEntities<SwitchFeatureProperty>(
        hitArea,
        source,
        SWITCH_FEATURE_DATA_PROPERTY,
        options,
    );
}

function setSwitchFeatureProperty(feature: Feature<OlPoint>, data: SwitchFeatureProperty) {
    feature.set(SWITCH_FEATURE_DATA_PROPERTY, data);
}
