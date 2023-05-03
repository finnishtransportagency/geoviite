import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelViewType } from 'infra-model/infra-model-slice';
import TabContainer from 'infra-model/tabs/tab-container';

export const InfraModelMainView: React.FC = () => {
    return (
        <div className={styles['infra-model-main']}>
            <Routes>
                <Route
                    path="/edit/:id"
                    element={<InfraModelViewContainer viewType={InfraModelViewType.EDIT} />}
                />
                <Route
                    path="/upload"
                    element={<InfraModelViewContainer viewType={InfraModelViewType.UPLOAD} />}
                />
                <Route path="/" element={<TabContainer />} />
            </Routes>
        </div>
    );
};
