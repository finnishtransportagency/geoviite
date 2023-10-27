import * as React from 'react';
import { NavLink } from 'react-router-dom';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
//import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';
import { EnvRestricted } from 'environment/env-restricted';
import { Environment } from 'environment/environment-info';
import { useTranslation } from 'react-i18next';
import { useInfraModelAppSelector } from 'store/hooks';
import { InfraModelTabType } from 'infra-model/infra-model-slice';
import { InfraModelLink } from 'app-bar/infra-model-link';
import { getPVDocumentCount } from 'infra-model/infra-model-api';
import { useLoader } from 'utils/react-utils';
import { getChangeTimes } from 'common/change-time-api';
import DataProductsMenu from 'app-bar/data-products-menu';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import { CloseableModal } from 'vayla-design-lib/closeable-modal/closeable-modal';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';

type Link = {
    link: string;
    name: string;
    type: Environment;
    qaId?: string;
};

const links: Link[] = [
    { link: '/', name: 'app-bar.frontpage', type: 'prod', qaId: 'frontpage-link' },
    {
        link: '/track-layout',
        name: 'app-bar.track-layout',
        type: 'prod',
        qaId: 'track-layout-link',
    },
    { link: '/registry', name: 'app-bar.register', type: 'test' },
    { link: '/infra-model', name: 'app-bar.infra-model', type: 'prod', qaId: 'infra-model-link' },
    { link: '/design-lib-demo', name: 'app-bar.components', type: 'dev' },
    { link: '/localization-demo', name: 'app-bar.localization', type: 'dev' },
];

export const AppBar: React.FC = () => {
    const { t } = useTranslation();
    const selectedInfraModelTab = useInfraModelAppSelector((state) => state.infraModelActiveTab);
    const changeTimes = getChangeTimes();
    const pvDocumentCounts = useLoader(() => getPVDocumentCount(), [changeTimes.pvDocument]);
    const exclamationPointVisibility = !!pvDocumentCounts && pvDocumentCounts?.suggested > 0;
    const [showMenu, setShowMenu] = React.useState(false);
    const [showLogoutConfirmation, setShowLogoutConfirmation] = React.useState(false);
    const menuRef = React.useRef(null);
    const menuOffsetX = 0;
    const menuOffsetY = 50;

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
        <nav className={styles['app-bar']}>
            <div className={styles['app-bar__title']}>
                <img className={styles['app-bar__logo']} src={geoviiteLogo} alt="Geoviite logo" />
                <div>Geoviite</div>
            </div>
            <ul className={styles['app-bar__links']}>
                {links.map((link) => {
                    return (
                        <EnvRestricted restrictTo={link.type} key={link.name}>
                            <li qa-id={link.qaId}>
                                {link.link !== '/infra-model' ? (
                                    <NavLink
                                        to={link.link}
                                        className={({ isActive }) =>
                                            `${styles['app-bar__link']} ${
                                                isActive ? styles['app-bar__link--active'] : ''
                                            }`
                                        }
                                        end>
                                        {t(link.name)}
                                    </NavLink>
                                ) : (
                                    <NavLink
                                        to={getInfraModelLink()}
                                        className={({ isActive }) =>
                                            getInfraModelLinkClassName(isActive)
                                        }
                                        end>
                                        <InfraModelLink
                                            exclamationPointVisibility={exclamationPointVisibility}
                                        />
                                    </NavLink>
                                )}
                            </li>
                        </EnvRestricted>
                    );
                })}
                <li>
                    <DataProductsMenu />
                </li>
            </ul>
            {
                // TODO Re-add logo when it has been specified where it should go
                /*<img
                className={styles['app-bar__vayla-logo']}
                src={vaylaLogo}
                alt="Väylävirasto logo"
            />*/
            }
            <ul className={styles['app-bar__links']}>
                <li
                    ref={menuRef}
                    title={t('app-bar.more')}
                    className={styles['app-bar__link']}
                    onClick={() => setShowMenu(!showMenu)}>
                    <Icons.Menu color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
                </li>
            </ul>
            {showMenu && (
                <CloseableModal
                    positionRef={menuRef}
                    onClickOutside={() => setShowMenu(false)}
                    offsetX={menuOffsetX}
                    offsetY={menuOffsetY}
                    className={styles['app-bar__menu']}>
                    <div className={styles['app-bar__menu-item']}>
                        <a onClick={() => setShowLogoutConfirmation(true)}>{t('app-bar.logout')}</a>
                    </div>
                </CloseableModal>
            )}
            {showLogoutConfirmation && (
                <Dialog
                    title={t('app-bar.logout')}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setShowLogoutConfirmation(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="confirm-logout"
                                onClick={() => (window.location.href = '/sso/logout?auth=1')}
                                variant={ButtonVariant.PRIMARY}>
                                {t('button.ok')}
                            </Button>
                        </div>
                    }>
                    {t('app-bar.logout-confirm')}
                </Dialog>
            )}
        </nav>
    );
};
