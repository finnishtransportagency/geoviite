import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelTabType, InfraModelViewType } from 'infra-model/infra-model-slice';
import TabContainer from 'infra-model/tabs/tab-container';
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
                <Route path="/" element={<TabContainer activeTab={activeInfraModelTab} />} />
                <Route
                    path="/plans"
                    element={<TabContainer activeTab={InfraModelTabType.PLAN} />}
                />
                <Route
                    path="/waiting-for-approval"
                    element={<TabContainer activeTab={InfraModelTabType.WAITING} />}
                />
                <Route
                    path="/rejected"
                    element={<TabContainer activeTab={InfraModelTabType.REJECTED} />}
                />
            </Routes>
        </div>
    );
};
