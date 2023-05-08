import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelViewType } from 'infra-model/infra-model-slice';
import TabContainer from 'infra-model/tabs/tab-container';
import { useCommonDataAppSelector } from 'store/hooks';
//import { InfraModelTabType } from 'common/common-slice';

export const InfraModelMainView: React.FC = () => {
    const activeTab = useCommonDataAppSelector((state) => state.infraModelActiveTab);

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

                <Route path="/" element={<TabContainer activeTab={activeTab} />} />
                {/*
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
                */}
            </Routes>
        </div>
    );
};
