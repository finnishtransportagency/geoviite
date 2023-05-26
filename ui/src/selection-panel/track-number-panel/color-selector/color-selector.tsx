import * as React from 'react';
import { useRef } from 'react';
import { createPortal } from 'react-dom';
import styles from './color-selector.scss';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';
import {
    getColor,
    getColors,
    TrackNumberColor,
    TrackNumberColorKey,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';

type ColorSelectorProps = {
    color?: TrackNumberColorKey;
    onSelectColor: (color: TrackNumberColorKey) => void;
} & React.HTMLProps<HTMLDivElement>;

const colorOpacity = '80'; //~50% opacity in hex

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
    const selectedColor = color && getColor(color);
    const isTransparent = selectedColor === TrackNumberColor.TRANSPARENT;
    const selectedColorClasses = createClassName(
        styles['color-square'],
        isTransparent && styles['color-square--transparent'],
    );

    return (
        <div ref={ref} {...props}>
            <div
                style={isTransparent ? {} : { backgroundColor: selectedColor + colorOpacity }}
                title={isTransparent ? t('selection-panel.color-selector.transparent') : ''}
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
    onSelectColor: (color: TrackNumberColorKey) => void;
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
                    className={`${styles['color-square']} ${styles['color-square--transparent']}`}
                    onClick={() => onSelectColor(TrackNumberColor.TRANSPARENT)}
                    title={t('selection-panel.color-selector.transparent')}
                />
                {getColors().map(([key, value]) => {
                    return (
                        <li
                            className={styles['color-square']}
                            key={key}
                            onClick={() => onSelectColor(key)}
                            style={{ backgroundColor: value + colorOpacity }}
                        />
                    );
                })}
            </ol>
        </div>,
        document.body,
    );
};

export default ColorSelector;
