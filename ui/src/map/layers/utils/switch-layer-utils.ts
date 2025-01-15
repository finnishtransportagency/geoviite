import {
    GeometryPlanLayout,
    LayoutSwitch,
    LayoutSwitchJoint,
} from 'track-layout/track-layout-model';
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
import {
    GeometryPlanLinkStatus,
    SuggestedLayoutSwitchJoint,
    SuggestedSwitch,
} from 'linking/linking-model';
import { JointNumber, SwitchStructure } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import { findMatchingEntities, pointToCoords } from 'map/layers/utils/layer-utils';
import { SearchItemsOptions } from 'map/layers/utils/layer-model';
import VectorSource from 'ol/source/Vector';
import { Rectangle } from 'model/geometry';
import { ValidatedSwitch } from 'publication/publication-model';
import { Selection } from 'selection/selection-model';
import * as Limits from 'map/layers/utils/layer-visibility-limits';
import { expectCoordinate, expectDefined } from 'utils/type-utils';
import { first } from 'utils/array-utils';

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
    valid: boolean,
): RenderFunction {
    const paddingX = 6;
    const paddingY = 4;
    const iconTextPaddingX = 6;
    const fontSize = TEXT_FONT_LARGE;
    const strokeWidth = valid ? 1 : 2;
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
            ({ name }, coord, ctx, { pixelRatio }) => {
                const [x, y] = expectCoordinate(coord);
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchLabel
                        : styles.unlinkedSwitchLabel
                    : styles.switchLabel;

                ctx.strokeStyle = valid ? styles.switchLabelBorder : styles.errorBright;
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
            (_, coord, ctx, { pixelRatio }) => {
                const [x, y] = expectCoordinate(coord);
                ctx.fillStyle = valid ? styles.switchTextColor : styles.errorDefault;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                ctx.drawImage(
                    valid ? switchImage : switchErrorImage,
                    x + (labelOffset + paddingX) * pixelRatio,
                    y - (iconSize / 2) * pixelRatio,
                    iconSize * pixelRatio,
                    iconSize * pixelRatio,
                );
            },

            ({ name }, coord, ctx, { pixelRatio }) => {
                const [x, y] = expectCoordinate(coord);
                ctx.fillStyle = styles.switchTextColor;
                ctx.fillStyle = valid ? styles.switchTextColor : styles.errorDefault;
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

function getSwitchRendererStrokeStyle(
    isGeometrySwitch: boolean,
    linked: boolean,
    valid: boolean,
): string {
    if (isGeometrySwitch) {
        return linked ? styles.linkedSwitchJointBorder : styles.unlinkedSwitchJointBorder;
    } else if (!valid) {
        return styles.errorBright;
    } else {
        return styles.switchJointBorder;
    }
}

function getSwitchRendererFillStyle(
    disabled: boolean,
    isGeometrySwitch: boolean,
    linked: boolean,
): string {
    if (disabled) {
        return styles.switchJointDisabled;
    } else if (isGeometrySwitch) {
        return linked ? styles.linkedSwitchJoint : styles.unlinkedSwitchJoint;
    } else {
        return styles.switchJoint;
    }
}

export function getSwitchRenderer(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    showLabel: boolean,
    linked: boolean,
    valid: boolean,
    disabled: boolean,
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
            (_, coord, ctx, { pixelRatio }) => {
                ctx.fillStyle = getSwitchRendererFillStyle(disabled, isGeometrySwitch, linked);
                ctx.strokeStyle = getSwitchRendererStrokeStyle(isGeometrySwitch, linked, valid);
                ctx.lineWidth = (valid ? 1 : 3) * pixelRatio;

                const [x, y] = expectCoordinate(coord);
                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },
            ({ name }, coord, ctx, { pixelRatio }) => {
                if (showLabel) {
                    ctx.fillStyle = styles.switchBackground;
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'middle';
                    const [x, y] = expectCoordinate(coord);

                    const textWidth = ctx.measureText(name).width;
                    const textX = x + (circleRadius + textCirclePadding) * pixelRatio;
                    const textY = y + pixelRatio;
                    const paddingHor = 2;
                    const paddingVer = 1;
                    const contentWidth = textWidth + (valid ? 0 : 1);
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

function getSwitchJointFillStyle(disabled: boolean, mainJoint: boolean): string {
    if (disabled) {
        return styles.switchJointDisabled;
    } else if (mainJoint) {
        return styles.switchMainJoint;
    } else {
        return styles.switchJoint;
    }
}

function getSwitchJointStrokeStyle(showErrorStyle: boolean, mainJoint: boolean): string {
    if (showErrorStyle) {
        return styles.errorBright;
    } else if (mainJoint) {
        return styles.switchMainJointBorder;
    } else {
        return styles.switchJointBorder;
    }
}

export function getJointRenderer(
    joint: LayoutSwitchJoint,
    mainJoint: boolean,
    valid: boolean = true,
    disabled: boolean = false,
): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const showErrorStyle = !valid && mainJoint;
    const circleRadius = showErrorStyle ? CIRCLE_RADIUS_LARGE + 1 : CIRCLE_RADIUS_LARGE;

    return getCanvasRenderer(
        joint,
        (ctx, { pixelRatio }) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = (!valid && mainJoint ? 3 : 1) * pixelRatio;
        },
        [
            (_, coord, ctx, { pixelRatio }) => {
                ctx.fillStyle = getSwitchJointFillStyle(disabled, mainJoint);
                ctx.strokeStyle = getSwitchJointStrokeStyle(showErrorStyle, mainJoint);

                const [x, y] = expectCoordinate(coord);
                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },

            ({ number }, coord, ctx) => {
                ctx.fillStyle = mainJoint
                    ? styles.switchMainJointTextColor
                    : styles.switchJointTextColor;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                const [x, y] = expectCoordinate(coord);
                ctx.fillText(switchJointNumberToString(number), x, y);
            },
        ],
    );
}

export function suggestedSwitchHasMatchOnJoint(
    suggestedSwitch: SuggestedSwitch,
    joint: JointNumber,
) {
    return Object.values(suggestedSwitch.trackLinks).some(
        (link) =>
            link.segmentJoints.some((sj) => sj.number == joint) ||
            link.topologyJoint?.number == joint,
    );
}

export function getLinkingJointRenderer(
    joint: SuggestedLayoutSwitchJoint,
    hasMatch: boolean,
    linked = false,
): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const circleRadius = CIRCLE_RADIUS_LARGE;

    return getCanvasRenderer(
        joint,
        (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
            ctx.font = `bold ${pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = pixelRatio;
        },
        [
            (_, coord, ctx, { pixelRatio }) => {
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

                const [x, y] = expectCoordinate(coord);
                drawCircle(ctx, x, y, circleRadius * pixelRatio);
            },

            ({ number }, coord, ctx) => {
                ctx.fillStyle = '#ffffff';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                const [x, y] = expectCoordinate(coord);
                ctx.fillText(switchJointNumberToString(number), x, y);
            },
        ],
    );
}

export const createGeometrySwitchFeatures = (
    status: GeometryPlanLinkStatus | undefined,
    visibleSwitches: (string | undefined)[],
    planLayout: GeometryPlanLayout,
    isSelected: (switchItem: LayoutSwitch) => boolean,
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
    showLargeSymbols: boolean,
    showLabels: boolean,
    switchStructures: SwitchStructure[],
) => {
    const switchLinkedStatus = status
        ? new Map(
              status.switches
                  .filter((s) => visibleSwitches.includes(s.id))
                  .map((switchItem) => [switchItem.id, switchItem.isLinked]),
          )
        : undefined;

    const isSwitchLinked = (switchItem: LayoutSwitch) =>
        (switchItem.sourceId && switchLinkedStatus?.get(switchItem.sourceId)) || false;

    return createSwitchFeatures(
        planLayout.switches.filter((s) => s.sourceId && visibleSwitches.includes(s.sourceId)),
        isSelected,
        isHighlighted,
        isSwitchLinked,
        showLargeSymbols,
        showLabels,
        false,
        planLayout.id,
        switchStructures,
    );
};

export const createLayoutSwitchFeatures = (
    resolution: number,
    selection: Selection,
    switches: LayoutSwitch[],
    disabled: boolean,
    switchStructures: SwitchStructure[],
    validationResult: ValidatedSwitch[],
) => {
    const largeSymbols = resolution <= Limits.SWITCH_LARGE_SYMBOLS;
    const showLabels = resolution <= Limits.SWITCH_LABELS;
    const isSelected = (switchItem: LayoutSwitch) => {
        return selection.selectedItems.switches.some((s) => s === switchItem.id);
    };

    const isHighlighted = (switchItem: LayoutSwitch) => {
        return selection.highlightedItems.switches.some((s) => s === switchItem.id);
    };

    return createSwitchFeatures(
        switches,
        isSelected,
        isHighlighted,
        () => false,
        largeSymbols,
        showLabels,
        disabled,
        undefined,
        switchStructures,
        validationResult,
    );
};

function createSwitchFeatures(
    layoutSwitches: LayoutSwitch[],
    isSelected: (switchItem: LayoutSwitch) => boolean,
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
    isLinked: (switchItem: LayoutSwitch) => boolean,
    showLargeSymbols: boolean,
    showLabels: boolean,
    disabled: boolean,
    planId?: GeometryPlanId,
    switchStructures?: SwitchStructure[],
    validationResult?: ValidatedSwitch[],
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
                disabled,
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
    disabled: boolean,
    planId?: GeometryPlanId,
    presentationJointNumber?: string | undefined,
    validationResult?: ValidatedSwitch | undefined,
): Feature<OlPoint>[] {
    const firstJoint = expectDefined(first(layoutSwitch.joints));

    const presentationJoint = layoutSwitch.joints.find(
        (joint) => joint.number == presentationJointNumber,
    );

    // Use presentation joint as main joint if possible, otherwise use first joint
    const switchFeature = new Feature({
        geometry: new OlPoint(pointToCoords(presentationJoint?.location ?? firstJoint.location)),
    });
    const valid = !validationResult?.errors || validationResult?.errors?.length === 0;

    switchFeature.setStyle(
        selected || highlighted
            ? getSelectedSwitchStyle(layoutSwitch, linked, valid)
            : getUnselectedSwitchStyle(
                  layoutSwitch,
                  largeSymbol,
                  showLabel,
                  linked,
                  valid,
                  disabled,
              ),
    );

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
                          true,
                          disabled,
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
                              disabled,
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
    valid: boolean,
    disabled: boolean,
): Style {
    return new Style({
        zIndex: 0,
        renderer: getSwitchRenderer(layoutSwitch, large, textLabel, linked, valid, disabled),
    });
}

function getSelectedSwitchStyle(
    layoutSwitch: LayoutSwitch,
    linked: boolean,
    valid: boolean = true,
): Style {
    return new Style({
        zIndex: 1,
        renderer: getSelectedSwitchLabelRenderer(layoutSwitch, linked, valid),
    });
}

function getSwitchJointStyle(
    joint: LayoutSwitchJoint,
    mainJoint: boolean,
    valid: boolean = true,
    disabled: boolean = false,
): Style {
    return new Style({
        zIndex: mainJoint ? 3 : 2,
        renderer: getJointRenderer(joint, mainJoint, valid, disabled),
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
