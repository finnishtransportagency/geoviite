import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelViewType } from 'infra-model/infra-model-store';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { ChangeTimes } from 'track-layout/track-layout-store';

export type InfraModelMainProps = {
    viewType: InfraModelViewType;
    changeTimes: ChangeTimes;
};

export const InfraModelMainView: React.FC<InfraModelMainProps> = ({
    viewType,
    changeTimes,
}: InfraModelMainProps) => {
    return (
        <div className={styles['infra-model-main']}>
            {viewType === InfraModelViewType.LIST && (
                <InfraModelListContainer changeTimes={changeTimes} />
            )}
            {(viewType === InfraModelViewType.UPLOAD || viewType == InfraModelViewType.EDIT) && (
                <InfraModelViewContainer changeTimes={changeTimes} />
            )}
        </div>
    );
};
