import * as React from 'react';
import styles from './operational-point-badge.scss';
import { OperationalPoint, OperationalPointId } from 'track-layout/track-layout-model';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createClassName } from 'vayla-design-lib/utils';
import { LayoutContext, TimeStamp } from 'common/common-model';
import { useOperationalPoint } from 'track-layout/track-layout-react-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { createDelegates } from 'store/store-utils';

type OperationalPointBadgeLinkProps = {
    operationalPointId: OperationalPointId;
    layoutContext: LayoutContext;
    changeTime: TimeStamp;
    onClick?: (operationalPointId: OperationalPointId) => void;
};

type OperationalPointBadgeProps = {
    operationalPoint: OperationalPoint;
    onClick?: React.MouseEventHandler;
    status?: OperationalPointBadgeStatus;
};

export enum OperationalPointBadgeStatus {
    DEFAULT = 'operational-point-badge--default',
    SELECTED = 'operational-point-badge--selected',
    DISABLED = 'operational-point-badge--disabled',
}

export const OperationalPointBadgeLink: React.FC<OperationalPointBadgeLinkProps> = ({
    operationalPointId,
    layoutContext,
    changeTime,
    onClick,
}: OperationalPointBadgeLinkProps) => {
    const op = useOperationalPoint(operationalPointId, layoutContext, changeTime);

    const clickAction = React.useCallback(() => {
        if (onClick) {
            onClick(operationalPointId);
        } else {
            const delegates = createDelegates(TrackLayoutActions);
            delegates.onSelect({ operationalPoints: [operationalPointId] });
            delegates.setToolPanelTab({
                id: operationalPointId,
                type: 'OPERATIONAL_POINT',
            });
        }
    }, [onClick, operationalPointId]);

    return op ? <OperationalPointBadge operationalPoint={op} onClick={clickAction} /> : <Spinner />;
};

export const OperationalPointBadge: React.FC<OperationalPointBadgeProps> = ({
    operationalPoint,
    onClick,
    status = OperationalPointBadgeStatus.DEFAULT,
}: OperationalPointBadgeProps) => {
    const classes = createClassName(
        styles['operational-point-badge'],
        status,
        onClick && styles['operational-point-badge--clickable'],
    );

    return (
        <span className={classes} onClick={onClick}>
            <span>{operationalPoint.name}</span>
        </span>
    );
};
