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
import { LayoutContext, TimeStamp } from 'common/common-model';

type ReferenceLinesPanelProps = {
    layoutContext: LayoutContext;
    trackNumberChangeTime: TimeStamp;
    referenceLines: LayoutReferenceLine[];
    onToggleReferenceLineSelection: (
        trackNumberId: LayoutTrackNumberId,
        referenceLine: ReferenceLineId,
    ) => void;
    selectedTrackNumbers?: LayoutTrackNumberId[];
    canSelectReferenceLine: boolean;
    max?: number;
    disabled: boolean;
};

const ReferenceLinesPanel: React.FC<ReferenceLinesPanelProps> = ({
    layoutContext,
    trackNumberChangeTime,
    referenceLines,
    onToggleReferenceLineSelection,
    selectedTrackNumbers,
    canSelectReferenceLine,
    max = 16,
    disabled,
}: ReferenceLinesPanelProps) => {
    const { t } = useTranslation();
    const [linesCount, setLinesCount] = React.useState(0);
    const [visibleLines, setVisibleLines] = React.useState<LayoutReferenceLine[]>([]);
    const trackNumbers = useTrackNumbers(layoutContext, trackNumberChangeTime);
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
                        (selectedId) => selectedId === line.trackNumberId,
                    );
                    const itemClassName = createClassName(
                        'reference-lines-panel__reference-line',
                        canSelectReferenceLine &&
                            'reference-lines-panel__reference-line--can-select',
                    );
                    const trackNumber = trackNumbers?.find((tn) => tn.id === line.trackNumberId);
                    const status = () => {
                        if (disabled) return ReferenceLineBadgeStatus.DISABLED;
                        else if (isSelected) return ReferenceLineBadgeStatus.SELECTED;
                        else return undefined;
                    };
                    return trackNumber ? (
                        <li
                            key={line.id}
                            className={itemClassName}
                            onClick={() =>
                                canSelectReferenceLine &&
                                onToggleReferenceLineSelection(line.trackNumberId, line.id)
                            }>
                            <ReferenceLineBadge trackNumber={trackNumber} status={status()} />
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
                        <React.Fragment key={line.id} />
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
