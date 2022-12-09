import * as React from 'react';
import styles from './reference-line.scss';
import {
    LayoutReferenceLine,
    LayoutTrackNumberId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { createClassName } from 'vayla-design-lib/utils';
import { useTranslation } from 'react-i18next';
import { compareByField } from 'utils/array-utils';
import {
    ReferenceLineBadge,
    ReferenceLineBadgeStatus,
} from 'geoviite-design-lib/alignment/reference-line-badge';
import { useTrackNumbers } from 'track-layout/track-layout-react-utils';
import { PublishType, TimeStamp } from 'common/common-model';

type ReferenceLinesPanelProps = {
    publishType: PublishType;
    trackNumberChangeTime: TimeStamp;
    referenceLines: LayoutReferenceLine[];
    onToggleReferenceLineSelection: (
        trackNumberId: LayoutTrackNumberId,
        referenceLine: ReferenceLineId,
    ) => void;
    selectedTrackNumbers?: LayoutTrackNumberId[];
    canSelectReferenceLine: boolean;
    max?: number;
};

const ReferenceLinesPanel: React.FC<ReferenceLinesPanelProps> = ({
    publishType,
    trackNumberChangeTime,
    referenceLines,
    onToggleReferenceLineSelection,
    selectedTrackNumbers,
    canSelectReferenceLine,
    max = 16,
}: ReferenceLinesPanelProps) => {
    console.log('Selection-Panel bind', selectedTrackNumbers);
    const { t } = useTranslation();
    const [linesCount, setLinesCount] = React.useState(0);
    const [visibleLines, setVisibleLines] = React.useState<LayoutReferenceLine[]>([]);
    const trackNumbers = useTrackNumbers(publishType, trackNumberChangeTime);
    React.useEffect(() => {
        if (referenceLines) {
            const sortedLines = [...referenceLines].sort((a, b) => {
                return compareByField(a, b, (l) => l.id);
            });

            setVisibleLines(sortedLines.length < max + 1 ? sortedLines : []);
            setLinesCount(sortedLines.length);
        } else {
            setVisibleLines([]);
            setLinesCount(0);
        }
    }, [referenceLines]);

    return (
        <div>
            <ol
                className={styles['reference-lines-panel__reference-lines']}
                qa-id="reference-lines-list">
                {visibleLines.map((line) => {
                    const isSelected = selectedTrackNumbers?.some(
                        (selectedId) => selectedId == line.trackNumberId,
                    );
                    const itemClassName = createClassName(
                        'reference-lines-panel__reference-line',
                        canSelectReferenceLine &&
                        'reference-lines-panel__reference-line--can-select',
                    );
                    const trackNumber = trackNumbers?.find((tn) => tn.id == line.trackNumberId);
                    return (
                        <li
                            key={line.id}
                            className={itemClassName}
                            onClick={() =>
                                canSelectReferenceLine &&
                                onToggleReferenceLineSelection(line.trackNumberId, line.id)
                            }>
                            {trackNumber && (
                                <ReferenceLineBadge
                                    trackNumber={trackNumber}
                                    status={
                                        isSelected ? ReferenceLineBadgeStatus.SELECTED : undefined
                                    }
                                />
                            )}
                            <span>{t('reference-line')}</span>
                        </li>
                    );
                })}
            </ol>
            {linesCount > max && (
                <span className={styles['reference-lines-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {linesCount === 0 && (
                <span className={styles['reference-lines-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};

export default React.memo(ReferenceLinesPanel);
