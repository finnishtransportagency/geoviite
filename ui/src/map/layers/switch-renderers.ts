import { LayoutSwitch, LayoutSwitchJoint } from 'track-layout/track-layout-model';
import { State } from 'ol/render';
import { createPointRenderer, drawCircle, drawRoundedRect } from 'map/layers/rendering';
import styles from '../map.module.scss';
import { RenderFunction } from 'ol/style/Style';
import SwitchIcon from 'vayla-design-lib/icon/glyphs/misc/switch.svg';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { SuggestedSwitchJoint } from 'linking/linking-model';

const switchImage: HTMLImageElement = new Image();
switchImage.src = `data:image/svg+xml;utf8,${encodeURIComponent(SwitchIcon)}`;

const TEXT_FONT_LARGE = 12;
const TEXT_FONT_SMALL = 10;
const CIRCLE_RADIUS_SMALL = 4.5;
const CIRCLE_RADIUS_LARGE = 6.5;

export function createSelectedSwitchLabelRenderer(
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

    return createPointRenderer<LayoutSwitch>(
        layoutSwitch,
        (ctx: CanvasRenderingContext2D, state: State) => {
            ctx.font = `bold ${state.pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = state.pixelRatio;
        },
        [
            (item, coordinates, ctx, state) => {
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchLabelBackground
                        : styles.notLinkedSwitchLabelBackground
                    : styles.switchLabelBackground;
                ctx.strokeStyle = isGeometrySwitch
                    ? linked
                        ? styles.linkedSwitchLabelBorder
                        : styles.notLinkedSwitchLabelBorder
                    : styles.switchLabelBorder;
                const textSize = ctx.measureText(item.name);
                const height = (Math.max(iconSize, fontSize) + 2 * paddingY) * state.pixelRatio;
                const width =
                    textSize.width +
                    (iconSize + 2 * paddingX + iconTextPaddingX) * state.pixelRatio;
                const x = coordinates[0] + labelOffset * state.pixelRatio;
                const y = coordinates[1] - height / 2;
                drawRoundedRect(ctx, x, y, width, height, 2 * state.pixelRatio);
            },

            (_item, coordinates, ctx, state) => {
                ctx.fillStyle = styles.switchTextColor;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                const x = coordinates[0] + (labelOffset + paddingX) * state.pixelRatio;
                const y = coordinates[1] - (iconSize / 2) * state.pixelRatio;
                ctx.drawImage(
                    switchImage,
                    x,
                    y,
                    iconSize * state.pixelRatio,
                    iconSize * state.pixelRatio,
                );
            },

            (_item, coordinates, ctx, state) => {
                ctx.fillStyle = styles.switchTextColor;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';

                const x =
                    coordinates[0] +
                    (labelOffset + iconSize + paddingX + iconTextPaddingX) * state.pixelRatio;
                const y = coordinates[1] + 2 * state.pixelRatio;
                ctx.fillText(layoutSwitch.name, x, y);
            },
        ],
    );
}

export function createUnselectedSwitchRenderer(
    layoutSwitch: LayoutSwitch,
    large: boolean,
    label: boolean,
    linked: boolean,
): RenderFunction {
    const fontSize = large ? TEXT_FONT_LARGE : TEXT_FONT_SMALL;
    const circleRadius = large ? CIRCLE_RADIUS_LARGE : CIRCLE_RADIUS_SMALL;
    const textCirclePadding = 4;
    const isGeometrySwitch = layoutSwitch.dataType == 'TEMP';
    return createPointRenderer<LayoutSwitch>(
        layoutSwitch,
        (ctx: CanvasRenderingContext2D, state: State) => {
            ctx.font = `bold ${state.pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = state.pixelRatio;
        },
        [
            (_item, coordinates, ctx, state) => {
                ctx.fillStyle = isGeometrySwitch
                    ? linked
                        ? styles.switchLinkedColor
                        : styles.switchUnlinkedColor
                    : styles.switchIconBackground;
                ctx.strokeStyle = isGeometrySwitch
                    ? linked
                        ? styles.switchLinkedColorStroke
                        : styles.switchUnlinkedColorStroke
                    : styles.switchIconBorder;

                drawCircle(ctx, coordinates[0], coordinates[1], circleRadius * state.pixelRatio);
            },

            (item, coordinates, ctx, state) => {
                if (label) {
                    ctx.fillStyle = styles.switchTextColor;
                    ctx.textAlign = 'left';
                    ctx.textBaseline = 'middle';

                    const x =
                        coordinates[0] + (circleRadius + textCirclePadding) * state.pixelRatio;
                    const y = coordinates[1] + state.pixelRatio;
                    ctx.fillText(item.name, x, y);
                }
            },
        ],
    );
}

export function createJointRenderer(joint: LayoutSwitchJoint, mainJoint: boolean): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const circleRadius = CIRCLE_RADIUS_LARGE;

    return createPointRenderer<LayoutSwitchJoint>(
        joint,
        (ctx: CanvasRenderingContext2D, state: State) => {
            ctx.font = `bold ${state.pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = state.pixelRatio;
        },
        [
            (_item, coordinates, ctx, state) => {
                ctx.fillStyle = mainJoint
                    ? styles.switchMainJointBackground
                    : styles.switchJointBackground;
                ctx.strokeStyle = mainJoint
                    ? styles.switchMainJointBorder
                    : styles.switchJointBorder;

                drawCircle(ctx, coordinates[0], coordinates[1], circleRadius * state.pixelRatio);
            },

            (_item, coordinates, ctx, _state) => {
                ctx.fillStyle = mainJoint ? styles.switchMainJointText : styles.switchJointText;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                ctx.fillText(
                    switchJointNumberToString(joint.number),
                    coordinates[0],
                    coordinates[1],
                );
            },
        ],
    );
}

export function createLinkingJointRenderer(
    joint: SuggestedSwitchJoint,
    _mainJoint: boolean,
    isLinked = false,
): RenderFunction {
    const fontSize = TEXT_FONT_SMALL;
    const circleRadius = CIRCLE_RADIUS_LARGE;
    const hasMatch = joint.matches.length > 0;

    return createPointRenderer<SuggestedSwitchJoint>(
        joint,
        (ctx: CanvasRenderingContext2D, state: State) => {
            ctx.font = `bold ${state.pixelRatio * fontSize}px sans-serif`;
            ctx.lineWidth = state.pixelRatio;
        },
        [
            (_item, coordinates, ctx, state) => {
                ctx.fillStyle = hasMatch
                    ? isLinked
                        ? styles.switchLinkedColor
                        : styles.switchUnlinkedColor
                    : styles.switchBackground;
                ctx.strokeStyle = hasMatch
                    ? isLinked
                        ? styles.switchLinkedColorStroke
                        : styles.switchUnlinkedColorStroke
                    : styles.switchBackground;

                drawCircle(ctx, coordinates[0], coordinates[1], circleRadius * state.pixelRatio);
            },

            (_item, coordinates, ctx, _state) => {
                ctx.fillStyle = '#ffffff';
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                ctx.fillText(
                    switchJointNumberToString(joint.number),
                    coordinates[0],
                    coordinates[1],
                );
            },
        ],
    );
}
