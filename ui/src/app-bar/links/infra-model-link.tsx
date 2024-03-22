import * as React from 'react';
import styles from '../app-bar.scss';
import { ExclamationPoint } from 'geoviite-design-lib/exclamation-point/exclamation-point';
import { useTranslation } from 'react-i18next';
import { getChangeTimes } from 'common/change-time-api';
import { useLoader } from 'utils/react-utils';
import { getPVDocumentCount } from 'infra-model/infra-model-api';
import { useCommonDataAppSelector, useInfraModelAppSelector } from 'store/hooks';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { NavLink } from 'react-router-dom';
import { VIEW_PV_DOCUMENTS, userHasPrivilege } from 'user/user-model';

export const InfraModelLink: React.FC = () => {
    const { t } = useTranslation();
    const userPrivileges = useCommonDataAppSelector(
        (state) => state.user?.role.privileges ?? [],
    ).map((p) => p.code);

    const changeTimes = getChangeTimes();
    const pvDocumentCounts = useLoader(() => getPVDocumentCount(), [changeTimes.pvDocument]);
    const exclamationPointVisibility =
        userHasPrivilege(userPrivileges, VIEW_PV_DOCUMENTS) &&
        !!pvDocumentCounts &&
        pvDocumentCounts?.suggested > 0;

    const selectedInfraModelTab = useInfraModelAppSelector((state) => state.infraModelActiveTab);

    function getInfraModelLink(): string {
        switch (selectedInfraModelTab) {
            case InfraModelTabType.PLAN:
                return '/infra-model/plans';
            case InfraModelTabType.WAITING:
                return '/infra-model/waiting-for-approval';
            case InfraModelTabType.REJECTED:
                return '/infra-model/rejected';
            default:
                return exhaustiveMatchingGuard(selectedInfraModelTab);
        }
    }

    function getInfraModelLinkClassName(isActive: boolean): string {
        return exclamationPointVisibility
            ? `${styles['app-bar__link']} ${
                  styles['app-bar__link--infra-model-with-exclamation-point']
              } ${isActive ? styles['app-bar__link--active'] : ''}`
            : `${styles['app-bar__link']} 
             ${isActive ? styles['app-bar__link--active'] : ''}`;
    }

    return (
        <NavLink
            to={getInfraModelLink()}
            className={({ isActive }) => getInfraModelLinkClassName(isActive)}
            end>
            <span className={styles['app-bar__link--container']}>
                {t('app-bar.infra-model')}
                {exclamationPointVisibility && (
                    <span className={styles['app-bar__link--exclamation-point']}>
                        <ExclamationPoint />
                    </span>
                )}
            </span>
        </NavLink>
    );
};
