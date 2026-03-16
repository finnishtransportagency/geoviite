import * as React from 'react';
import styles from '../app-bar.scss';
import { useTranslation } from 'react-i18next';
import { useInfraModelAppSelector } from 'store/hooks';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { NavLink } from 'react-router-dom';
import { createClassName } from 'vayla-design-lib/utils';

export const InfraModelLink: React.FC = () => {
    const { t } = useTranslation();
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

    return (
        <NavLink
            to={getInfraModelLink()}
            className={({ isActive }) =>
                createClassName(
                    styles['app-bar__link'],
                    isActive ? styles['app-bar__link--active'] : '',
                )
            }
            end>
            <span className={styles['app-bar__link--container']}>{t('app-bar.infra-model')}</span>
        </NavLink>
    );
};
