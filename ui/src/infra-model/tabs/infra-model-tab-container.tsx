import * as React from 'react';
import InfraModelTabs from 'infra-model/tabs/infra-model-tabs';
import styles from './infra-model-tabs.scss';
import { InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabsContainerProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabContainer: React.FC<TabsContainerProps> = ({ activeTab: activeTab }) => {
    return (
        <div className={styles['tab-container']}>
            <InfraModelTabs activeTab={activeTab} />
        </div>
    );
};

export default InfraModelTabContainer;
