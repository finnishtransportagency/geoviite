import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelTabType, InfraModelViewType } from 'infra-model/infra-model-slice';
import InfraModelTabContainer from 'infra-model/tabs/infra-model-tab-container';
import { useInfraModelAppSelector } from 'store/hooks';

export const InfraModelMainView: React.FC = () => {
    const activeInfraModelTab = useInfraModelAppSelector((state) => state.infraModelActiveTab);

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
                <Route
                    path="/"
                    element={<InfraModelTabContainer activeTab={activeInfraModelTab} />}
                />
                <Route
                    path="/plans"
                    element={<InfraModelTabContainer activeTab={InfraModelTabType.PLAN} />}
                />
                <Route
                    path="/waiting-for-approval"
                    element={<InfraModelTabContainer activeTab={InfraModelTabType.WAITING} />}
                />
                <Route
                    path="/rejected"
                    element={<InfraModelTabContainer activeTab={InfraModelTabType.REJECTED} />}
                />
            </Routes>
        </div>
    );
};
