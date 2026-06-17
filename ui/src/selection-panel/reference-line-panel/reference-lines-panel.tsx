import * as React from 'react';
import styles from './reference-line.scss';
import { LayoutTrackNumberId, LayoutTrackNumber } from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';
import { compareByField } from 'utils/array-utils';
import {
    TrackNumberBadge,
    TrackNumberBadgeStatus,
} from 'geoviite-design-lib/alignment/track-number-badge';

type ReferenceLinesPanelProps = {
    trackNumbers: LayoutTrackNumber[];
    onToggleTrackNumberSelection: (trackNumberId: LayoutTrackNumberId) => void;
    selectedTrackNumbers?: LayoutTrackNumberId[];
    canSelectTrackNumber: boolean;
    max?: number;
    disabled: boolean;
};

const ReferenceLinesPanel: React.FC<ReferenceLinesPanelProps> = ({
    trackNumbers,
    onToggleTrackNumberSelection,
    selectedTrackNumbers,
    canSelectTrackNumber: canSelectReferenceLine,
    max = 16,
    disabled,
}: ReferenceLinesPanelProps) => {
    const { t } = useTranslation();
    const [trackNumberCount, setTrackNumberCount] = React.useState(0);
    const [visibleTrackNumbers, setVisibleTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);
    React.useEffect(() => {
        if (trackNumbers) {
            const sortedTrackNumbers = [...trackNumbers].sort((a, b) => {
                return compareByField(a, b, (l) => l.id);
            });

            setVisibleTrackNumbers(sortedTrackNumbers.length < max + 1 ? sortedTrackNumbers : []);
            setTrackNumberCount(sortedTrackNumbers.length);
        } else {
            setVisibleTrackNumbers([]);
            setTrackNumberCount(0);
        }
    }, [trackNumbers]);

    return (
        <div>
            <ol
                className={styles['reference-lines-panel__reference-lines']}
                qa-id="reference-lines-list">
                {visibleTrackNumbers.map((tn) => {
                    const isSelected = selectedTrackNumbers?.some(
                        (selectedId) => selectedId === tn.id,
                    );
                    const itemClassName = createClassName(
                        'reference-lines-panel__reference-line',
                        canSelectReferenceLine &&
                            'reference-lines-panel__reference-line--can-select',
                    );
                    const trackNumber = trackNumbers?.find((tn) => tn.id === tn.id);
                    const status = () => {
                        if (disabled) return TrackNumberBadgeStatus.DISABLED;
                        else if (isSelected) return TrackNumberBadgeStatus.SELECTED;
                        else return undefined;
                    };
                    return trackNumber ? (
                        <li
                            key={tn.id}
                            className={itemClassName}
                            onClick={() =>
                                canSelectReferenceLine && onToggleTrackNumberSelection(tn.id)
                            }>
                            <TrackNumberBadge trackNumber={trackNumber} status={status()} />
                            <span
                                className={
                                    disabled
                                        ? styles['reference-lines-panel__reference-line--disabled']
                                        : undefined
                                }>
                                {t('reference-line')}
                            </span>
                        </li>
                    ) : (
                        <React.Fragment key={tn.id} />
                    );
                })}
            </ol>
            {trackNumberCount > max && (
                <span className={styles['reference-lines-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {trackNumberCount === 0 && (
                <span className={styles['reference-lines-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};

export default React.memo(ReferenceLinesPanel);
