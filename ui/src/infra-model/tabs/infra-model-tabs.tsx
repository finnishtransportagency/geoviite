import React from 'react';
import InfraModelTabNavItem from 'infra-model/tabs/infra-model-tab-nav-item';
import InfraModelTabContent from 'infra-model/tabs/infra-model-tab-content';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { VelhoFileListContainer } from 'infra-model/velho/velho-file-list';
import styles from 'infra-model/tabs/infra-model-tabs.scss';
import { useLoader } from 'utils/react-utils';
import { getVelhoDocumentCount } from 'infra-model/infra-model-api';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabs: React.FC<TabsProps> = ({ activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.velhoDocument);
    const numberOfInfraModelFiles = useInfraModelAppSelector(
        (state) => state.infraModelList.totalCount,
    );
    const documentCounts = useLoader(() => getVelhoDocumentCount(), [changeTime]);

    return (
        <div className={styles['tabs__tab-container']}>
            <ul className={styles['tabs__nav']}>
                <InfraModelTabNavItem
                    title={t('im-form.tabs.plans', { number: numberOfInfraModelFiles })}
                    tabId={InfraModelTabType.PLAN}
                    activeTab={activeTab}
                    exclamationPointVisible={false}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-waiting', {
                        number: documentCounts?.suggested,
                    })}
                    tabId={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                    exclamationPointVisible={!!documentCounts && documentCounts?.suggested > 0}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.velho-files-rejected', {
                        number: documentCounts?.rejected,
                    })}
                    tabId={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                    exclamationPointVisible={false}
                />
            </ul>
            <div className={styles['tabs__outlet']}>
                <InfraModelTabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer changeTimes={changeTimes} />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <VelhoFileListContainer listMode={'SUGGESTED'} changeTime={changeTime} />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <VelhoFileListContainer listMode={'REJECTED'} changeTime={changeTime} />
                </InfraModelTabContent>
            </div>
        </div>
    );
};

export default InfraModelTabs;
