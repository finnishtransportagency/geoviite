import React from 'react';
import copySvg from './glyphs/action/copy.svg';
import downloadSvg from './glyphs/action/download.svg';
import appendSvg from './glyphs/action/append.svg';
import deleteSvg from './glyphs/action/delete.svg';
import filterSvg from './glyphs/action/filter.svg';
import editSvg from './glyphs/action/edit.svg';
import searchSvg from './glyphs/action/search.svg';
import switchDirectionSvg from './glyphs/action/switch-direction.svg';
import setDateSvg from './glyphs/action/set-date.svg';
import setTimeSvg from './glyphs/action/set-time.svg';
import ascending from './glyphs/action/ascending.svg';
import descending from './glyphs/action/descending.svg';
import targetSvg from './glyphs/action/target.svg';
import clearSvg from './glyphs/action/clear.svg';
import infoSvg from './glyphs/status/info.svg';
import deniedSvg from './glyphs/status/denied.svg';
import selectedSvg from './glyphs/status/selected.svg';
import layersSvg from './glyphs/misc/layers.svg';
import closeSvg from './glyphs/navigation/close.svg';
import navigationDownSvg from './glyphs/navigation/down.svg';
import statusErrorSvg from './glyphs/status/error.svg';
import kmPostSvg from './glyphs/misc/kmpost.svg';
import kmPostSelectedSvg from './glyphs/misc/kmpost-selected.svg';
import kmPostDisabledSvg from './glyphs/misc/kmpost-disabled.svg';
import switchSvg from './glyphs/misc/switch.svg';
import chevronSvg from './glyphs/navigation/chevron.svg';
import eyeSvg from './glyphs/status/eye.svg';
import tickSvg from './glyphs/navigation/tick.svg';
import moreSvg from './glyphs/navigation/more.svg';
import lockSvg from './glyphs/status/lock.svg';
import vectorRight from './glyphs/navigation/vector-right.svg';
import redoSvg from './glyphs/action/redo.svg';
import measureSvg from './glyphs/action/measure.svg';
import selectSvg from './glyphs/action/select.svg';
import fullScreen from './glyphs/navigation/fullscreen.svg';
import exitFullScreen from './glyphs/navigation/exit-fullscreen.svg';
import externalLink from './glyphs/navigation/external-link.svg';
import menuSvg from './glyphs/navigation/menu.svg';
import positionPinSvg from './glyphs/misc/position-pin.svg';
import styles from './icon.scss';
import { createClassName } from 'vayla-design-lib/utils';

/**
 *
 * Usage:
 *
 * <code>
 * import Icons from 'path to this file';
 *
 * // in JSX
 * <Icons.Download/>
 *
 * // and use props for fine tuning
 * <Icons.Download size={IconSize.SMALL}/>
 *
 * </code>
 *
 * Icons in figma:
 * https://www.figma.com/file/Czt3xfeJ32WWwngxy8ypN8/Design-Library?node-id=1%3A120
 *
 * How to add new icon:
 * - Export SVG from Figma
 * - Go to https://www.figma.com/file/Czt3xfeJ32WWwngxy8ypN8/Design-Library?node-id=1%3A120
 * - Select icon component(s) to export, e.g. "action/download"
 * - Then export SVG version into "glyphs" folder, sub folders are generated automatically
 * - Add some code into this file
 * - Add an import for the exported SVG file(s), like this: import someSvg from 'some-folder/some.svg'
 * - Add new mapping into "iconNameToSvgMap", like this: SomeIcon: someSvg
 * - Then you can use it like this: <Icon.SomeIcon/>
 */

const iconNameToSvgMap = {
    // Navigation
    Close: closeSvg,
    Chevron: chevronSvg,
    Down: navigationDownSvg,
    Tick: tickSvg,
    More: moreSvg,
    Menu: menuSvg,
    VectorRight: vectorRight,
    FullScreen: fullScreen,
    ExitFullScreen: exitFullScreen,
    ExternalLink: externalLink,

    // Status
    Info: infoSvg,
    StatusError: statusErrorSvg,
    Selected: selectedSvg,
    Eye: eyeSvg,
    Lock: lockSvg,
    Denied: deniedSvg,

    // Actions
    Copy: copySvg,
    Download: downloadSvg,
    Append: appendSvg,
    Filter: filterSvg,
    Search: searchSvg,
    Delete: deleteSvg,
    Edit: editSvg,
    SwitchDirection: switchDirectionSvg,
    Ascending: ascending,
    Descending: descending,
    Redo: redoSvg,
    SetDate: setDateSvg,
    SetTime: setTimeSvg,
    Measure: measureSvg,
    Select: selectSvg,
    Target: targetSvg,
    Clear: clearSvg,

    // Misc
    Layers: layersSvg,
    Switch: switchSvg,
    PositionPin: positionPinSvg,
};

const iconNameToSvgMapStaticColor = {
    // Misc
    KmPost: kmPostSvg,
    KmPostSelected: kmPostSelectedSvg,
    KmPostDisabled: kmPostDisabledSvg,
};

export enum IconSize {
    SMALL = 'icon--size-small',
    MEDIUM = 'icon--size-medium',
    MEDIUM_SMALL = 'icon--size-medium-small',
    LARGE = 'icon--size-large',
    INHERIT = 'icon--size-inherit',
    ORIGINAL = '',
}

export enum IconRotation {
    ROTATE_180 = 'icon--rotate-180',
}

export enum IconColor {
    INHERIT = 'icon--color-inherit',
    ORIGINAL = 'icon--color-original',
    DISABLED = 'icon--color-disabled',
}

export type IconProps = {
    size?: IconSize;
    rotation?: IconRotation;
    color?: IconColor;
    ref?: React.RefObject<SVGSVGElement>;
    extraClassName?: string;
} & Pick<React.HTMLProps<HTMLOrSVGElement>, 'onClick'>;

export type IconComponent = React.FC<IconProps>;

export type SvgIconProps = IconProps & {
    svg: string;
};

export type SvgIconComponent = React.FC<SvgIconProps>;

const SvgIcon: SvgIconComponent = ({
    svg,
    size = IconSize.MEDIUM,
    color,
    extraClassName,
    ...props
}: SvgIconProps) => {
    let svgContent = svg
        // grab the content of the given SVG tag
        .replace(/^.*?>|<\/svg.*/gi, '');

    if (color !== IconColor.ORIGINAL) {
        svgContent = svgContent // remove "fill" attributes to control colors with css
            .replace(/fill=".*?"/gi, '');
    }

    const className = createClassName(
        'icon',
        size && styles[size],
        props.rotation && styles[props.rotation],
        color && styles[color],
        extraClassName,
    );

    const parsedSize = parseSize(svg) || [24, 24];
    const sizeProps =
        size == IconSize.ORIGINAL
            ? {
                  width: parsedSize[0],
                  height: parsedSize[1],
              }
            : {};
    const fullProps = { ...props, ...sizeProps };

    return (
        <svg
            className={className}
            viewBox={parseViewBox(svg)}
            dangerouslySetInnerHTML={{ __html: svgContent }}
            {...fullProps}
        />
    );
};

function parseViewBox(svg: string): string {
    const viewBoxMatch = /viewBox="([\s0-9]+)"/.exec(svg);
    const viewBox = viewBoxMatch && viewBoxMatch[1];
    return viewBox ? viewBox : '0 0 24 24';
}

function parseSize(svg: string): number[] | undefined {
    const heightMatch = /height="([0-9]+)"/.exec(svg);
    const widthMatch = /width="([0-9]+)"/.exec(svg);

    const height = heightMatch && heightMatch[1];
    const width = widthMatch && widthMatch[1];
    if (height && width) {
        return [parseInt(width, 10), parseInt(height, 10)];
    } else {
        return undefined;
    }
}

export function makeHigherOrderSvgIcon(svg: string): IconComponent {
    const SvgIconHoc: React.FC<IconProps> = (props: IconProps) => {
        return <SvgIcon svg={svg} {...props} />;
    };
    return SvgIconHoc;
}

export type IconGlyph = keyof typeof iconNameToSvgMap | keyof typeof iconNameToSvgMapStaticColor;

export type GlyphIconProps = IconProps & {
    glyph: IconGlyph;
};

export type GlyphIconComponent = React.FC<GlyphIconProps>;

export const Icon: GlyphIconComponent = ({ glyph, ...props }: GlyphIconProps) => {
    const svg = { ...iconNameToSvgMap, ...iconNameToSvgMapStaticColor }[glyph];
    return <SvgIcon svg={svg} {...props} />;
};

// Create a map of higher-order icon components, so that
// <Icon glyph={"Selected"}/> can be used like <Icons.Selected/>

type IconsMap = {
    [key in IconGlyph]: IconComponent;
};

function makeHigherOrderIcon(glyph: IconGlyph, defaultProps?: IconProps): IconComponent {
    const IconHoc: React.FC<IconProps> = (iconProps: IconProps) => {
        return <Icon {...{ ...defaultProps, ...iconProps }} glyph={glyph} />;
    };
    return IconHoc;
}

function makeIconsMap(glyphs: IconGlyph[], props?: IconProps): IconsMap {
    return glyphs.reduce((iconsMap: IconsMap, glyph: IconGlyph) => {
        return {
            ...iconsMap,
            [glyph]: makeHigherOrderIcon(glyph, props),
        };
    }, {} as IconsMap);
}

function makeMultiIcon(Icon: IconComponent): IconComponent {
    const IconHoc: React.FC<IconProps> = (iconProps: IconProps) => {
        return (
            <div>
                <Icon {...iconProps} extraClassName={'icon--multi-first'} />
                <Icon {...iconProps} extraClassName={'icon--multi-second'} />
            </div>
        );
    };
    return IconHoc;
}

export const Icons = {
    ...makeIconsMap(Object.keys(iconNameToSvgMap) as IconGlyph[]),
    ...makeIconsMap(Object.keys(iconNameToSvgMapStaticColor) as IconGlyph[], {
        color: IconColor.ORIGINAL,
    }),
    UpMultiple: makeMultiIcon(makeHigherOrderIcon('Ascending')),
    DownMultiple: makeMultiIcon(makeHigherOrderIcon('Descending')),
    DeleteMultiple: makeMultiIcon(makeHigherOrderIcon('Delete')),
};
