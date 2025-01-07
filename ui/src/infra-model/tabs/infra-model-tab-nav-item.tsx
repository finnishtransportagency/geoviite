import React from 'react';
import { useAppNavigate } from 'common/navigate';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import styles from './infra-model-tabs.scss';
import { createDelegates } from 'store/store-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { TabHeader } from 'geoviite-design-lib/tab-header/tab-header';

export type TabNavItemProps = {
    tabId: InfraModelTabType;
    title: string;
    activeTab: InfraModelTabType;
    exclamationPointVisible: boolean;
};

const InfraModelTabNavItem: React.FC<TabNavItemProps> = ({
    tabId,
    title,
    activeTab,
    exclamationPointVisible,
}) => {
    const navigate = useAppNavigate();
    const delegates = createDelegates(infraModelActionCreators);

    const handleClick = () => {
        delegates.setInfraModelActiveTab(tabId);
        switch (tabId) {
            case InfraModelTabType.PLAN:
                return navigate('inframodel-plans');
            case InfraModelTabType.WAITING:
                return navigate('inframodel-waiting');
            case InfraModelTabType.REJECTED:
                return navigate('inframodel-rejected');
            default:
                return exhaustiveMatchingGuard(tabId);
        }
    };

    return (
        <TabHeader
            selected={activeTab === tabId}
            onClick={handleClick}
            qa-id={`infra-model-nav-tab-${tabId}`}>
            {title}
            {exclamationPointVisible && (
                <span className={styles['tabs__exclamation-point-container']}>
                    <ExclamationPoint />
                </span>
            )}
        </TabHeader>
    );
};
export default InfraModelTabNavItem;
