import * as React from 'react';
import styles from './track-number-panel.scss';
import { LayoutTrackNumber, LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { fieldComparator } from 'utils/array-utils';
import { Radio } from 'vayla-design-lib/radio/radio';
import { useTranslation } from 'react-i18next';
import { TrackNumberDiagramLayerSetting } from 'map/map-model';
import {
    getDefaultColorKey,
    TrackNumberColorKey,
} from 'selection-panel/track-number-panel/color-selector/color-selector-utils';
import ColorSelector from 'selection-panel/track-number-panel/color-selector/color-selector';

type TrackNumberPanelProps = {
    trackNumbers: LayoutTrackNumber[];
    settings: TrackNumberDiagramLayerSetting;
    onSelectTrackNumber: (trackNumber: LayoutTrackNumber) => void;
    onSelectColor: (trackNumberId: LayoutTrackNumberId, color: TrackNumberColorKey) => void;
    selectedTrackNumbers: LayoutTrackNumberId[];
    max?: number;
    canSelect?: boolean;
};

const TrackNumberPanel: React.FC<TrackNumberPanelProps> = ({
    trackNumbers,
    settings,
    onSelectTrackNumber,
    onSelectColor,
    selectedTrackNumbers,
    max = 16,
    canSelect = true,
}: TrackNumberPanelProps) => {
    const { t } = useTranslation();
    const [sortedTrackNumbers, setSortedTrackNumbers] = React.useState<LayoutTrackNumber[]>([]);

    React.useEffect(() => {
        const visibleTrackNumbers =
            trackNumbers.length > max && selectedTrackNumbers.length
                ? trackNumbers.filter((tn) => selectedTrackNumbers.some((stn) => stn == tn.id))
                : [...trackNumbers];

        setSortedTrackNumbers(visibleTrackNumbers.sort(fieldComparator((tn) => tn.number)));
    }, [trackNumbers, selectedTrackNumbers]);

    return (
        <div>
            {sortedTrackNumbers.length <= max && (
                <ol className={styles['track-number-panel__track-numbers']}>
                    {sortedTrackNumbers.map((trackNumber) => {
                        const isSelected = selectedTrackNumbers?.some((s) => s == trackNumber.id);
                        return (
                            <li
                                className={styles['track-number-panel__track-number']}
                                key={trackNumber.id}>
                                <div>
                                    <span
                                        onMouseUp={() =>
                                            canSelect && onSelectTrackNumber(trackNumber)
                                        }>
                                        <Radio
                                            disabled={!canSelect}
                                            checked={isSelected}
                                            readOnly={true}
                                            name={trackNumber.number}
                                        />
                                        {trackNumber.number}
                                    </span>
                                    <ColorSelector
                                        color={
                                            settings[trackNumber.id]?.color ??
                                            getDefaultColorKey(trackNumber.id)
                                        }
                                        onSelectColor={(color) =>
                                            onSelectColor(trackNumber.id, color)
                                        }
                                        className={'track-number-panel__color-selector'}
                                    />
                                </div>
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
