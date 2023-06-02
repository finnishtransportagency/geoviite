import React from 'react';
import styles from './infra-model-main.scss';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { InfraModelViewContainer } from 'infra-model/view/infra-model-view-container';
import { Route, Routes } from 'react-router-dom';
import { InfraModelViewType } from 'infra-model/infra-model-slice';
import { useCommonDataAppSelector } from 'store/hooks';
//testing
export const inframodelEditPath = `/edit`;

export const InfraModelMainView: React.FC = () => {
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    return (
        <div className={styles['infra-model-main']}>
            <Routes>
                <Route
                    path={`${inframodelEditPath}/:id`}
                    element={<InfraModelViewContainer viewType={InfraModelViewType.EDIT} />}
                />
                <Route
                    path="/upload"
                    element={<InfraModelViewContainer viewType={InfraModelViewType.UPLOAD} />}
                />
                <Route path="/" element={<InfraModelListContainer changeTimes={changeTimes} />} />
            </Routes>
        </div>
    );
};
