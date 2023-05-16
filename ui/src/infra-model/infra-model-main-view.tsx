import React from 'react';
import styles from './infra-model-main.scss';
import {
    InfraModelViewContainer,
    InfraModelViewType,
} from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { useInfraModelAppSelector } from 'store/hooks';
import InfraModelTabs from 'infra-model/tabs/infra-model-tabs';

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
                    path="/import/:id"
                    element={<InfraModelViewContainer viewType={InfraModelViewType.IMPORT} />}
                />
                <Route
                    path="/upload"
                    element={<InfraModelViewContainer viewType={InfraModelViewType.UPLOAD} />}
                />
                <Route path="/" element={<InfraModelTabs activeTab={activeInfraModelTab} />} />
                <Route
                    path="/plans"
                    element={<InfraModelTabs activeTab={InfraModelTabType.PLAN} />}
                />
                <Route
                    path="/waiting-for-approval"
                    element={<InfraModelTabs activeTab={InfraModelTabType.WAITING} />}
                />
                <Route
                    path="/rejected"
                    element={<InfraModelTabs activeTab={InfraModelTabType.REJECTED} />}
                />
            </Routes>
        </div>
    );
};
