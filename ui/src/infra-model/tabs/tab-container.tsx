import * as React from 'react';
import Tabs from 'infra-model/tabs/tabs';
import styles from './tabs.scss';
import { InfraModelTabType } from 'infra-model/infra-model-slice';

export type TabsContainerProps = {
    activeTab: InfraModelTabType;
};

const TabContainer: React.FC<TabsContainerProps> = ({ activeTab: activeTab }) => {
    return (
        <div className={styles['tab-container']}>
            <Tabs activeTab={activeTab} />
        </div>
    );
};

export default TabContainer;
