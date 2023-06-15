import React from 'react';
import InfraModelTabNavItem from 'infra-model/tabs/infra-model-tab-nav-item';
import InfraModelTabContent from 'infra-model/tabs/infra-model-tab-content';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { InfraModelListContainer } from 'infra-model/list/infra-model-list-container';
import { useTranslation } from 'react-i18next';
import { infraModelActionCreators, InfraModelTabType } from 'infra-model/infra-model-slice';
import { PVFileListContainer } from 'infra-model/projektivelho/pv-file-list';
import styles from 'infra-model/tabs/infra-model-tabs.scss';
import { useLoader } from 'utils/react-utils';
import { getPVDocumentCount } from 'infra-model/infra-model-api';
import { getGeometryPlanHeadersBySearchTerms } from 'geometry/geometry-api';
import { createDelegates } from 'store/store-utils';

export type TabsProps = {
    activeTab: InfraModelTabType;
};

const InfraModelTabs: React.FC<TabsProps> = ({ activeTab }) => {
    const { t } = useTranslation();
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    const changeTime = useCommonDataAppSelector((state) => state.changeTimes.pvDocument);
    const infraModelListDelegates = createDelegates(infraModelActionCreators);
    const state = useInfraModelAppSelector((state) => state.infraModelList);
    const numberOfInfraModelFiles = useInfraModelAppSelector(
        (state) => state.infraModelList.totalCount,
    );
    const documentCounts = useLoader(() => getPVDocumentCount(), [changeTime]);

    // Handle search plans side effect
    React.useEffect(() => {
        if (state.searchState == 'start') {
            infraModelListDelegates.onPlanFetchStart();
            getGeometryPlanHeadersBySearchTerms(
                state.pageSize,
                state.page * state.pageSize,
                undefined,
                state.searchParams.sources,
                state.searchParams.trackNumberIds,
                state.searchParams.freeText,
                state.searchParams.sortBy,
                state.searchParams.sortOrder,
            )
                .then((page) => {
                    infraModelListDelegates.onPlansFetchReady({
                        plans: page.items,
                        searchParams: state.searchParams,
                        totalCount: page.totalCount,
                    });
                })
                .catch((e) => infraModelListDelegates.onPlanFetchError(e?.toString()));
        }
    }, [state.searchState]);

    React.useEffect(() => {
        infraModelListDelegates.onPlanChangeTimeChange();
    }, [changeTimes.geometryPlan]);

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
                    title={t('im-form.tabs.projektivelho-files-waiting', {
                        number: documentCounts?.suggested,
                    })}
                    tabId={InfraModelTabType.WAITING}
                    activeTab={activeTab}
                    exclamationPointVisible={!!documentCounts && documentCounts?.suggested > 0}
                />
                <InfraModelTabNavItem
                    title={t('im-form.tabs.projektivelho-files-rejected', {
                        number: documentCounts?.rejected,
                    })}
                    tabId={InfraModelTabType.REJECTED}
                    activeTab={activeTab}
                    exclamationPointVisible={false}
                />
            </ul>
            <div className={styles['tabs__outlet']}>
                <InfraModelTabContent tabId={InfraModelTabType.PLAN} activeTab={activeTab}>
                    <InfraModelListContainer
                        changeTimes={changeTimes}
                        clearInfraModelState={infraModelListDelegates.clearInfraModelState}
                        onSearchParamsChange={infraModelListDelegates.onSearchParamsChange}
                        onPrevPage={infraModelListDelegates.onPrevPage}
                        onNextPage={infraModelListDelegates.onNextPage}
                    />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.WAITING} activeTab={activeTab}>
                    <PVFileListContainer listMode={'SUGGESTED'} changeTime={changeTime} />
                </InfraModelTabContent>
                <InfraModelTabContent tabId={InfraModelTabType.REJECTED} activeTab={activeTab}>
                    <PVFileListContainer listMode={'REJECTED'} changeTime={changeTime} />
                </InfraModelTabContent>
            </div>
        </div>
    );
};

export default InfraModelTabs;
