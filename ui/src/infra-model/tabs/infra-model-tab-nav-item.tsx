import React from 'react';
import { useAppNavigate } from 'common/navigate';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import styles from './infra-model-tabs.scss';
import { createDelegates } from 'store/store-utils';

export type TabNavItemProps = {
    tabId: InfraModelTabType;
    title: string;
    activeTab: InfraModelTabType;
};

const InfraModelTabNavItem: React.FC<TabNavItemProps> = ({ tabId, title, activeTab }) => {
    const navigate = useAppNavigate();
    const delegates = createDelegates(infraModelActionCreators);

    delegates.setInfraModelActiveTab(activeTab);

    const handleClick = () => {
        switch (tabId) {
            case InfraModelTabType.PLAN:
                return navigate('inframodel-plans');
            case InfraModelTabType.WAITING:
                return navigate('inframodel-waiting');
            case InfraModelTabType.REJECTED:
                return navigate('inframodel-rejected');
        }
    };

    //TODO exclamation point if there are rows in Velho list
    return (
        <li
            onClick={handleClick}
            className={activeTab === tabId ? styles['active'] : styles['inactive']}>
            {title}
            {tabId === InfraModelTabType.WAITING && (
                <span className={styles['exclamation-point-container']}>
                    <ExclamationPoint />
                </span>
            )}
        </li>
    );
};
export default InfraModelTabNavItem;
