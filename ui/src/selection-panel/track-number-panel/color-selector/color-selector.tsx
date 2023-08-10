import * as React from 'react';
import { useRef } from 'react';
import styles from './color-selector.scss';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';
import {
    getColor,
    getColors,
    TrackNumberColor,
    TrackNumberColorKey,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import { CloseableModal } from 'geoviite-design-lib/closeable-modal/closeable-modal';

type ColorSelectorProps = {
    color?: TrackNumberColorKey;
    onSelectColor: (color: TrackNumberColorKey) => void;
} & React.HTMLProps<HTMLDivElement>;

const colorOpacity = '80'; //~50% opacity in hex

const ColorSelector: React.FC<ColorSelectorProps> = ({ color, onSelectColor }) => {
    const [showSelector, setShowSelector] = React.useState(false);
    const ref = useRef<HTMLDivElement>(null);
    const { t } = useTranslation();

    const selectedColor = color && getColor(color);
    const isTransparent = selectedColor === TrackNumberColor.TRANSPARENT;
    const selectedColorClasses = createClassName(
        styles['color-square'],
        isTransparent && styles['color-square--transparent'],
    );
    const colorSelectorModalOffsetX = 32;
    const colorSelectorModalOffsetY = 0;

    return (
        <React.Fragment>
            <div
                ref={ref}
                style={isTransparent ? {} : { backgroundColor: selectedColor + colorOpacity }}
                title={isTransparent ? t('selection-panel.color-selector.transparent') : ''}
                className={selectedColorClasses}
                onClick={() => setShowSelector(true)}
            />
            {showSelector && (
                <CloseableModal
                    positionRef={ref}
                    onClickOutside={() => setShowSelector(false)}
                    offsetX={colorSelectorModalOffsetX}
                    offsetY={colorSelectorModalOffsetY}>
                    <div className={styles['color-selector-menu']}>
                        <ol>
                            <li
                                className={`${styles['color-square']} ${styles['color-square--transparent']}`}
                                onClick={() => {
                                    setShowSelector(false);
                                    onSelectColor(TrackNumberColor.TRANSPARENT);
                                }}
                                title={t('selection-panel.color-selector.transparent')}
                            />
                            {getColors().map(([key, value]) => (
                                <li
                                    className={styles['color-square']}
                                    key={key}
                                    onClick={() => {
                                        setShowSelector(false);
                                        onSelectColor(key);
                                    }}
                                    style={{ backgroundColor: value + colorOpacity }}
                                />
                            ))}
                        </ol>
                    </div>
                </CloseableModal>
            )}
        </React.Fragment>
    );
};

export default ColorSelector;
