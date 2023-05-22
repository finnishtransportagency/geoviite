import React from 'react';
import InfraModelTabNavItem from 'infra-model/tabs/infra-model-tab-nav-item';
import InfraModelTabContent from 'infra-model/tabs/infra-model-tab-content';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { VelhoFileListContainer } from 'infra-model/velho/velho-file-list';
import styles from 'infra-model/tabs/infra-model-tabs.scss';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabs: React.FC<TabsProps> = ({ activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const numberOfInfraModelFiles = useInfraModelAppSelector(
        (state) => state.infraModelList.totalCount,
    );
    const numberOfInfraModelFileCandidates = 500;
    const numberOfRejectedInfraModelFiles = 1500;

    return (
        <div className={styles['tabs__tab-container']}>
            <ul className={styles['tabs__nav']}>
                <InfraModelTabNavItem
                    title={t('im-form.tabs.plans', { number: numberOfInfraModelFiles })}
                    tabId={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-waiting', {
                        number: numberOfInfraModelFileCandidates,
                    })}
                    tabId={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-rejected', {
                        number: numberOfRejectedInfraModelFiles,
                    })}
                    tabId={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                />
            </ul>
            <div className={styles['tabs__outlet']}>
                <InfraModelTabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer changeTimes={changeTimes} />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <VelhoFileListContainer />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <p>Hello from rejected tab</p>
                </InfraModelTabContent>
            </div>
        </div>
    );
};

export default InfraModelTabs;
