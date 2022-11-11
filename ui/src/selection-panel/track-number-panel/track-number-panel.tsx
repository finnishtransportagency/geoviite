import * as React from 'react';
import styles from './track-number-panel.scss';
import { LayoutTrackNumber } from 'track-layout/track-layout-model';
import { fieldComparator } from 'utils/array-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { useTranslation } from 'react-i18next';

type TrackNumberPanelProps = {
    trackNumbers: LayoutTrackNumber[];
    onSelectTrackNumber: (trackNumber: LayoutTrackNumber) => void;
    selectedTrackNumbers: LayoutTrackNumber[];
    max?: number;
};

const TrackNumberPanel: React.FC<TrackNumberPanelProps> = ({
    trackNumbers,
    onSelectTrackNumber,
    selectedTrackNumbers,
    max = 24,
}: TrackNumberPanelProps) => {
    const { t } = useTranslation();
    const [sortedTrackNumbers, setSortedTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);

    React.useEffect(() => {
        const visibleTrackNumbers =
            trackNumbers.length > max && selectedTrackNumbers.length
                ? trackNumbers.filter((tn) =>
                      selectedTrackNumbers.some((stn) => stn.number == tn.number),
                  )
                : [...trackNumbers];

        setSortedTrackNumbers(visibleTrackNumbers.sort(fieldComparator((tn) => tn.number)));
    }, [trackNumbers, selectedTrackNumbers]);

    return (
        <div>
            {sortedTrackNumbers.length <= max && (
                <ol className={styles['track-number-panel__track-numbers']}>
                    {sortedTrackNumbers.map((trackNumber) => {
                        const isSelected = selectedTrackNumbers?.some(
                            (selected) => selected.id == trackNumber.id,
                        );
                        return (
                            <li
                                className={styles['track-number-panel__track-number']}
                                key={trackNumber.id}
                                onMouseUp={() => onSelectTrackNumber(trackNumber)}>
                                <Radio
                                    checked={isSelected}
                                    readOnly={true}
                                    name={trackNumber.number}
                                />
                                {trackNumber.number}
                            </li>
                        );
                    })}
                </ol>
            )}
            {sortedTrackNumbers.length > max && (
                <span className={styles['track-number-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {sortedTrackNumbers.length === 0 && (
                <span className={styles['track-number-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};

export default React.memo(TrackNumberPanel);
