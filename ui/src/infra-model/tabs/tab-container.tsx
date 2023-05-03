import * as React from 'react';
import Tabs from 'infra-model/tabs/tabs';
import styles from './tabs.scss';

const TabContainer: React.FC = () => {
    return (
        <div className={styles['tab-container']}>
            <Tabs />
        </div>
    );
};

export default TabContainer;
