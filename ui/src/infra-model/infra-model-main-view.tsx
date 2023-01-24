import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { ChangeTimes } from 'track-layout/track-layout-store';
import { Route, Routes } from 'react-router-dom';
import { InfraModelViewType } from 'infra-model/infra-model-store';

export type InfraModelMainProps = {
    changeTimes: ChangeTimes;
};

export const InfraModelMainView: React.FC<InfraModelMainProps> = ({
    changeTimes,
}: InfraModelMainProps) => {
    return (
        <div className={styles['infra-model-main']}>
            <Routes>
                <Route
                    path="/edit/:id"
                    element={
                        <InfraModelViewContainer
                            viewType={InfraModelViewType.EDIT}
                            changeTimes={changeTimes}
                        />
                    }
                />
                <Route
                    path="/upload"
                    element={
                        <InfraModelViewContainer
                            viewType={InfraModelViewType.UPLOAD}
                            changeTimes={changeTimes}
                        />
                    }
                />
                <Route path="/" element={<InfraModelListContainer changeTimes={changeTimes} />} />
            </Routes>
        </div>
    );
};
