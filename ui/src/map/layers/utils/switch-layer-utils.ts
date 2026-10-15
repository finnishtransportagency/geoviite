import {
    GeometryPlanLayout,
    LayoutSwitch,
    LayoutSwitchId,
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
import { GeometryPlanLinkStatus, SuggestedSwitch } from 'linking/linking-model';
import { JointNumber, SwitchStructure } from 'common/common-model';
import { GeometryPlanId } from 'geometry/geometry-model';
import Feature from 'ol/Feature';
import { Point as OlPoint } from 'ol/geom';
import {
    findMatchingEntities,
    pointToCoords,
    getPlanarDistanceUnwrapped,
} from 'map/layers/utils/layer-utils';
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
const NEARBY_SWITCH_PROXIMITY_THRESHOLD = 1; // meters, this could be changed to a pre-existing pixel distance calculation if needed

const mainJointLocation = (layoutSwitch: LayoutSwitch) =>
    layoutSwitch.joints.find((joint) => joint.role === 'MAIN')?.location;

// Switches stacked in the same location, sorted by name. The first one draws the shared label.
export type SwitchCluster = {
    members: LayoutSwitch[];
    highlightedIds: LayoutSwitchId[];
};

// Groups switches into clusters as connected components of the "closer than threshold" relation,
// so that every member of a cluster gets exactly the same cluster. Switches are bucketed into a
// grid with the threshold as cell size, so only neighbouring cells need to be compared.
const buildSwitchClusters = (
    switches: LayoutSwitch[],
    isHighlighted: (switchItem: LayoutSwitch) => boolean,
): Map<LayoutSwitchId, SwitchCluster> => {
    const located = switches.flatMap((layoutSwitch) => {
        const location = mainJointLocation(layoutSwitch);
        return location ? [{ layoutSwitch, x: location.x, y: location.y }] : [];
    });

    const parents = located.map((_, index) => index);
    const findRoot = (index: number): number => {
        let root = index;
        while (parents[root] !== root) root = expectDefined(parents[root]);
        let current = index;
        while (parents[current] !== root) {
            const next = expectDefined(parents[current]);
            parents[current] = root;
            current = next;
        }
        return root;
    };

    const cellOf = (value: number) => Math.floor(value / NEARBY_SWITCH_PROXIMITY_THRESHOLD);
    const grid = new Map<string, number[]>();
    located.forEach((item, index) => {
        const cx = cellOf(item.x);
        const cy = cellOf(item.y);
        for (let dx = -1; dx <= 1; dx++) {
            for (let dy = -1; dy <= 1; dy++) {
                const neighbours = grid.get(`${cx + dx}_${cy + dy}`) ?? [];
                neighbours.forEach((otherIndex) => {
                    const other = expectDefined(located[otherIndex]);
                    if (
                        getPlanarDistanceUnwrapped(item.x, item.y, other.x, other.y) <
                        NEARBY_SWITCH_PROXIMITY_THRESHOLD
                    ) {
                        parents[findRoot(index)] = findRoot(otherIndex);
                    }
                });
            }
        }
        const ownKey = `${cx}_${cy}`;
        grid.set(ownKey, [...(grid.get(ownKey) ?? []), index]);
    });

    const components = new Map<number, LayoutSwitch[]>();
    located.forEach((item, index) => {
        const root = findRoot(index);
        components.set(root, [...(components.get(root) ?? []), item.layoutSwitch]);
    });

    const clusters = new Map<LayoutSwitchId, SwitchCluster>();
    components.forEach((unsorted) => {
        if (unsorted.length < 2) return;
        const members = [...unsorted].sort((a, b) => a.name.localeCompare(b.name));
        const cluster = {
            members,
            highlightedIds: members.filter(isHighlighted).map((s) => s.id),
        };
        members.forEach((member) => clusters.set(member.id, cluster));
    });
    return clusters;
};

const CLUSTER_PADDING_VER = 4;
const CLUSTER_TEXT_CIRCLE_PADDING = 4;
const CLUSTER_SEARCH_REACH_PX = 300;

type ClusterLayout = {
    fontSize: number;
    paddingHor: number;
    lineHeight: number;
    textOffsetX: number;
    totalHeight: number;
};

const getClusterLayout = (large: boolean, rowCount: number): ClusterLayout => {
    const fontSize = large ? TEXT_FONT_LARGE : TEXT_FONT_SMALL;
    const circleRadius = large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL;
    // Stacked labels have more padding, so they are easier to click
    const stacked = rowCount > 1;
    const paddingHor = stacked ? 6 : 2;
    const lineHeight = fontSize + (stacked ? 9 : 3);
    return {
        fontSize,
        paddingHor,
        lineHeight,
        textOffsetX: circleRadius + CLUSTER_TEXT_CIRCLE_PADDING + paddingHor - 2,
        totalHeight: rowCount * lineHeight + 2 * CLUSTER_PADDING_VER,
    };
};

let measureContext: CanvasRenderingContext2D | null | undefined;
const measureTextWidth = (text: string, fontSize: number): number => {
    if (measureContext === undefined) {
        measureContext = document.createElement('canvas').getContext('2d');
    }
    if (!measureContext) return 0;
    measureContext.font = `bold ${fontSize}px sans-serif`;
    return measureContext.measureText(text).width;
};

// Label geometry in CSS pixels relative to the switch point, y growing downwards.
// Shared by the renderer and the hover hit test so they cannot drift apart.
type ClusterBox = {
    left: number;
    top: number;
    width: number;
    rowTop: (index: number) => number;
};

const getClusterBox = (
    layout: ClusterLayout,
    members: LayoutSwitch[],
    valid: boolean = true,
): ClusterBox => {
    const maxTextWidth = Math.max(
        ...members.map((sw) => measureTextWidth(sw.name, layout.fontSize)),
    );
    const top = -layout.totalHeight / 2;
    return {
        left: layout.textOffsetX - layout.paddingHor - 1,
        top,
        width: maxTextWidth + (valid ? 0 : 1) + layout.paddingHor * 2,
        rowTop: (index) => top + CLUSTER_PADDING_VER + index * layout.lineHeight,
    };
};

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
    const isGeometrySwitch = layoutSwitch.dataType === 'TEMP';
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
    cluster?: SwitchCluster,
): RenderFunction {
    const fontSize = large ? TEXT_FONT_LARGE : TEXT_FONT_SMALL;
    const circleRadius = large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL;
    const isGeometrySwitch = layoutSwitch.dataType === 'TEMP';
    const rows = cluster ? cluster.members : [layoutSwitch];
    // Only the first switch of a stack draws the shared label, the others only draw their circle
    const drawsLabel = showLabel && rows[0]?.id === layoutSwitch.id;
    const layout = getClusterLayout(large, rows.length);
    // Measure the label once here instead of on every render frame
    const box = drawsLabel ? getClusterBox(layout, rows, valid) : undefined;
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
            (_, coord, ctx, { pixelRatio }) => {
                if (!box) return;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                const [x, y] = expectCoordinate(coord);

                const textX = x + layout.textOffsetX * pixelRatio;
                const boxLeft = x + box.left * pixelRatio;
                const boxWidth = box.width * pixelRatio;
                const rowCenter = (i: number) =>
                    y + (box.rowTop(i) + layout.lineHeight / 2) * pixelRatio;

                ctx.fillStyle = styles.switchBackground;
                ctx.beginPath();
                drawRect(
                    ctx,
                    boxLeft,
                    y + box.top * pixelRatio,
                    boxWidth,
                    layout.totalHeight * pixelRatio,
                );

                // Draw all highlights before any text so a highlight never covers another row
                ctx.fillStyle = styles.switchRowHighlight;
                rows.forEach((sw, i) => {
                    if (cluster?.highlightedIds.includes(sw.id)) {
                        ctx.beginPath();
                        drawRect(
                            ctx,
                            boxLeft,
                            y + box.rowTop(i) * pixelRatio,
                            boxWidth,
                            layout.lineHeight * pixelRatio,
                        );
                    }
                });

                // The hovered row is heavier than the rest. Many system fonts have no weight
                // above bold, so a thin stroke adds the extra weight there.
                ctx.fillStyle = styles.switchTextColor;
                ctx.strokeStyle = styles.switchTextColor;
                ctx.lineJoin = 'round';
                rows.forEach((sw, i) => {
                    const hovered = cluster?.highlightedIds.includes(sw.id) ?? false;
                    ctx.font = `${hovered ? '900' : 'bold'} ${pixelRatio * fontSize}px sans-serif`;
                    ctx.fillText(sw.name, textX, rowCenter(i) + pixelRatio);
                    if (hovered) {
                        ctx.lineWidth = 0.6 * pixelRatio;
                        ctx.strokeText(sw.name, textX, rowCenter(i) + pixelRatio);
                    }
                });
            },
        ],
    );
}

export const getDeletedSwitchRenderer = (large: boolean): RenderFunction =>
    getCanvasRenderer(
        undefined,
        (ctx: CanvasRenderingContext2D, { pixelRatio }: State) => {
            ctx.lineWidth = pixelRatio;
        },
        [
            (_, coord, ctx, { pixelRatio }) => {
                ctx.fillStyle = styles.deletedSwitchJoint;
                ctx.strokeStyle = styles.deletedSwitchJointBorder;
                ctx.lineWidth = pixelRatio;

                const [x, y] = expectCoordinate(coord);
                drawCircle(
                    ctx,
                    x,
                    y,
                    (large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL) * pixelRatio,
                );

                // Draw cross
                ctx.moveTo(x - 2 * pixelRatio, y - 2 * pixelRatio);
                ctx.lineTo(x + 2 * pixelRatio, y + 2 * pixelRatio);

                ctx.moveTo(x - 2 * pixelRatio, y + 2 * pixelRatio);
                ctx.lineTo(x + 2 * pixelRatio, y - 2 * pixelRatio);
                ctx.stroke();
            },
        ],
    );

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
    return Object.values(suggestedSwitch.trackLinks).some((link) =>
        link.suggestedLinks?.joints?.some((suggestedJoint) => suggestedJoint.jointNumber === joint),
    );
}

export function getLinkingJointRenderer(
    joint: LayoutSwitchJoint,
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
    const clusters = buildSwitchClusters(
        layoutSwitches.filter((s) => !isSelected(s)),
        isHighlighted,
    );

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
                clusters.get(layoutSwitch.id),
            );
        });
}

export function createSwitchFeature(
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
    cluster?: SwitchCluster,
): Feature<OlPoint>[] {
    const firstJoint = expectDefined(first(layoutSwitch.joints));

    const presentationJoint = layoutSwitch.joints.find(
        (joint) => joint.number === presentationJointNumber,
    );

    // Use presentation joint as main joint if possible, otherwise use first joint
    const switchFeature = new Feature({
        geometry: new OlPoint(pointToCoords(presentationJoint?.location ?? firstJoint.location)),
    });
    const valid = !validationResult?.errors || validationResult?.errors?.length === 0;

    // A highlighted switch in a stack keeps the shared label, where its row is emphasized
    const showSelectedLabel = selected || (highlighted && !cluster);
    switchFeature.setStyle(
        showSelectedLabel
            ? getSelectedSwitchStyle(layoutSwitch, highlighted, linked, valid)
            : getUnselectedSwitchStyle(
                  layoutSwitch,
                  largeSymbol,
                  showLabel,
                  linked,
                  valid,
                  disabled,
                  cluster,
              ),
    );

    const isLabelOwner =
        showLabel && !showSelectedLabel && cluster?.members[0]?.id === layoutSwitch.id;

    const clusterLayout =
        isLabelOwner && cluster ? getClusterLayout(largeSymbol, cluster.members.length) : undefined;

    setSwitchFeatureProperty(switchFeature, {
        switch: layoutSwitch,
        planId: planId,
        clusterLabel:
            clusterLayout && cluster
                ? {
                      members: cluster.members,
                      layout: clusterLayout,
                      box: getClusterBox(clusterLayout, cluster.members),
                  }
                : undefined,
    });

    const jointFeatures =
        selected || highlighted
            ? layoutSwitch.joints.map((joint, index) => {
                  const feature = new Feature({
                      geometry: new OlPoint(pointToCoords(joint.location)),
                  });

                  feature.setStyle(
                      getSwitchJointStyle(
                          joint,
                          highlighted,
                          // Again, use presentation joint as main joint if found, otherwise use first one
                          presentationJoint
                              ? joint.number === presentationJointNumber
                              : index === 0,
                          !validationResult || validationResult.errors.length === 0,
                          disabled,
                      ),
                  );

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
    cluster?: SwitchCluster,
): Style {
    return new Style({
        zIndex: 0,
        renderer: getSwitchRenderer(
            layoutSwitch,
            large,
            textLabel,
            linked,
            valid,
            disabled,
            cluster,
        ),
    });
}

function getSelectedSwitchStyle(
    layoutSwitch: LayoutSwitch,
    highlighted: boolean,
    linked: boolean,
    valid: boolean = true,
): Style {
    return new Style({
        zIndex: highlighted ? 2 : 1,
        renderer: getSelectedSwitchLabelRenderer(layoutSwitch, linked, valid),
    });
}

function getSwitchJointStyle(
    joint: LayoutSwitchJoint,
    highlighted: boolean,
    mainJoint: boolean,
    valid: boolean = true,
    disabled: boolean = false,
): Style {
    // Z-index logic
    // 1: non-selected switches (no joints visible)
    // 2: selected switch (joints visible) regular joints so it's not covered by other switches
    // 3: selected switch main joint, so it's not covered by regular ones
    // +10: for highlighted (joints also visible) same joint-ordering as above, but draw over all
    return new Style({
        zIndex: (mainJoint ? 3 : 2) + (highlighted ? 10 : 0),
        renderer: getJointRenderer(joint, mainJoint, valid, disabled),
    });
}

type SwitchFeatureProperty = {
    switch: LayoutSwitch;
    planId: GeometryPlanId | undefined;
    clusterLabel?: { members: LayoutSwitch[]; layout: ClusterLayout; box: ClusterBox };
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

// Finds the switch whose row in a stacked-switch label is under the cursor (center of the hit area)
export function findHoveredClusterSwitch(
    hitArea: Rectangle,
    source: VectorSource,
    resolution: number,
): LayoutSwitchId | undefined {
    const [minX = 0, minY = 0, maxX = 0, maxY = 0] = hitArea.getExtent();
    const cursorX = (minX + maxX) / 2;
    const cursorY = (minY + maxY) / 2;
    const reach = CLUSTER_SEARCH_REACH_PX * resolution;
    const features = source.getFeaturesInExtent([
        cursorX - reach,
        cursorY - reach,
        cursorX + reach,
        cursorY + reach,
    ]);

    for (const feature of features) {
        const cluster = (feature.get(SWITCH_FEATURE_DATA_PROPERTY) as SwitchFeatureProperty)
            ?.clusterLabel;
        const [switchX, switchY] =
            (feature.getGeometry() as OlPoint | undefined)?.getCoordinates() ?? [];
        if (!cluster || switchX === undefined || switchY === undefined) continue;

        const { layout, box } = cluster;
        // Screen y grows downwards, map y upwards
        const dx = (cursorX - switchX) / resolution;
        const dy = (switchY - cursorY) / resolution;
        const row = Math.floor((dy - box.rowTop(0)) / layout.lineHeight);
        if (
            dx >= box.left &&
            dx <= box.left + box.width &&
            row >= 0 &&
            row < cluster.members.length
        ) {
            return cluster.members[row]?.id;
        }
    }
    return undefined;
}
