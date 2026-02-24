import * as React from 'react';
import styles from './operational-point-panel.scss';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { compareNamed } from 'common/common-model';
import {
    OperationalPointBadge,
    OperationalPointBadgeStatus,
} from 'geoviite-design-lib/operational-point/operational-point-badge';

type OperationalPointPanelProps = {
    operationalPoints: OperationalPoint[];
    onToggleOperationalPointSelection: (op: OperationalPoint) => void;
    selectedOperationalPoints?: OperationalPointId[];
    max?: number;
    disabled: boolean;
};

export const OperationalPointPanel: React.FC<OperationalPointPanelProps> = ({
    operationalPoints,
    onToggleOperationalPointSelection,
    selectedOperationalPoints,
    max = 16,
    disabled,
}: OperationalPointPanelProps) => {
    const { t } = useTranslation();
    const [visibleOperationalPoints, setVisibleOperationalPoints] = React.useState(
        [] as OperationalPoint[],
    );

    React.useEffect(() => {
        if (operationalPoints) {
            const sortedPoints = [...operationalPoints].sort(compareNamed);

            setVisibleOperationalPoints(sortedPoints.length < max + 1 ? sortedPoints : []);
        } else {
            setVisibleOperationalPoints([]);
        }
    }, [operationalPoints, max]);

    return (
        <ol className={styles['operational-point-panel__operational-points']}>
            {visibleOperationalPoints.map((op) => {
                const isSelected = selectedOperationalPoints?.some(
                    (selectedOp) => selectedOp === op.id,
                );
                const status = disabled
                    ? OperationalPointBadgeStatus.DISABLED
                    : isSelected
                      ? OperationalPointBadgeStatus.SELECTED
                      : OperationalPointBadgeStatus.DEFAULT;

                return (
                    <li key={op.id}>
                        <OperationalPointBadge
                            operationalPoint={op}
                            status={status}
                            onClick={() => onToggleOperationalPointSelection(op)}
                        />
                    </li>
                );
            })}
            {visibleOperationalPoints.length > max && (
                <span className={styles['operational-point-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {visibleOperationalPoints.length === 0 && (
                <span className={styles['operational-point-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </ol>
    );
};
