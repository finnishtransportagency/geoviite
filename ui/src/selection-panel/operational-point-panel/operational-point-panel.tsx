import * as React from 'react';
import styles from './operational-point-panel.scss';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';
import { createClassName } from 'vayla-design-lib/utils';
import { compareNamed } from 'common/common-model';

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

    const [operationalPointsCount, setOperationalPointsCount] = React.useState(0);
    const [visibleOperationalPoints, setVisibleOperationalPoints] = React.useState(
        [] as OperationalPoint[],
    );

    React.useEffect(() => {
        if (operationalPoints) {
            const sortedPoints = [...operationalPoints].sort(compareNamed);

            setVisibleOperationalPoints(sortedPoints.length < max + 1 ? sortedPoints : []);
            setOperationalPointsCount(sortedPoints.length);
        } else {
            setVisibleOperationalPoints([]);
            setOperationalPointsCount(0);
        }
    }, [operationalPoints, max]);

    return (
        <div>
            <ul className={styles['operational-point-panel__operational-points']}>
                {visibleOperationalPoints.map((op) => {
                    const isSelected = selectedOperationalPoints?.some(
                        (selectedOp) => selectedOp === op.id,
                    );
                    const containerClassName = createClassName(
                        styles['operational-point-panel__item-container'],
                        disabled && styles['operational-point-panel__item-container--disabled'],
                        !disabled &&
                            isSelected &&
                            styles['operational-point-panel__item-container--selected'],
                    );

                    return (
                        <li key={op.id} className={containerClassName}>
                            <span
                                onClick={() => onToggleOperationalPointSelection(op)}
                                className={styles['operational-point-panel__item']}>
                                <span className={styles['operational-point-panel__name']}>
                                    {op.name}
                                </span>
                            </span>
                        </li>
                    );
                })}
            </ul>
            {operationalPointsCount > max && (
                <span className={styles['operational-point-panel__subtitle']}>{`${t(
                    'selection-panel.zoom-closer',
                )}`}</span>
            )}

            {operationalPointsCount === 0 && (
                <span className={styles['operational-point-panel__subtitle']}>{`${t(
                    'selection-panel.no-results',
                )}`}</span>
            )}
        </div>
    );
};
