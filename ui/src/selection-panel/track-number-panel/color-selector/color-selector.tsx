import * as React from 'react';
import { useRef } from 'react';
import { createPortal } from 'react-dom';
import styles from './color-selector.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';

export type TrackNumberColorKey = keyof typeof TrackNumberColor;
export enum TrackNumberColor {
    GRAY = '#858585',
    FIG = '#00b0cc',
    BLUE = '#0066cc',
    GREEN = '#27b427',
    LEMON = '#ffc300',
    RED = '#de3618',
    PITAYA = '#e50083',
    EGGPLANT = '#a050a0',
}

type ColorSelectorProps = {
    color?: TrackNumberColorKey;
    onSelectColor: (color: TrackNumberColorKey | undefined) => void;
} & React.HTMLProps<HTMLDivElement>;

const ColorSelector: React.FC<ColorSelectorProps> = ({ color, onSelectColor, ...props }) => {
    const [showSelector, setShowSelector] = React.useState(false);
    const ref = useRef<HTMLDivElement>(null);
    const { t } = useTranslation();

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (ref.current && !ref.current.contains(event.target as HTMLElement)) {
                setShowSelector(false);
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [ref]);

    const { x, y } = ref.current?.getBoundingClientRect() ?? {};
    const hasColor = !!(color && TrackNumberColor[color]);
    const selectedColorClasses = createClassName(
        styles['color-square'],
        !hasColor && styles['color-square--none'],
    );

    return (
        <div ref={ref} {...props}>
            <div
                style={{ ...(hasColor ? { backgroundColor: TrackNumberColor[color] } : {}) }}
                title={hasColor ? '' : t('selection-panel.color-selector.none')}
                className={selectedColorClasses}
                onClick={() => setShowSelector(!showSelector)}
            />
            {showSelector && x && y && (
                <ColorSelectorMenu
                    x={x + 32}
                    y={y}
                    onSelectColor={(color) => {
                        onSelectColor(color);
                        setShowSelector(false);
                    }}
                />
            )}
        </div>
    );
};

type ColorSelectorMenuProps = {
    x: number;
    y: number;
    onSelectColor: (color: TrackNumberColorKey | undefined) => void;
};

const ColorSelectorMenu: React.FC<ColorSelectorMenuProps> = ({ x, y, onSelectColor }) => {
    const { t } = useTranslation();

    return createPortal(
        <div
            className={styles['color-selector-menu']}
            style={{ top: y, left: x }}
            onClick={(e) => e.stopPropagation()}>
            <ol>
                <li
                    className={`${styles['color-square']} ${styles['color-square--none']}`}
                    onClick={() => onSelectColor(undefined)}
                    title={t('selection-panel.color-selector.none')}
                />
                {Object.entries(TrackNumberColor).map(([key, value]) => {
                    return (
                        <li
                            className={styles['color-square']}
                            key={key}
                            onClick={() => onSelectColor(key as TrackNumberColorKey)}
                            style={{ backgroundColor: value }}
                        />
                    );
                })}
            </ol>
        </div>,
        document.body,
    );
};

export default ColorSelector;
