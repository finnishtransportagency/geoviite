import * as React from 'react';
import styles from './app-bar.scss';
import geoviiteLogo from 'geoviite-design-lib/geoviite-logo.svg';
//import vaylaLogo from 'vayla-design-lib/logo/vayla-logo.svg';
import { EnvRestricted } from 'environment/env-restricted';
import { Environment } from 'environment/environment-info';
import { useTranslation } from 'react-i18next';
import { InfraModelLink } from 'app-bar/links/infra-model-link';
import DataProductsMenu from 'app-bar/data-products-menu';
import AppBarMoreMenu from 'app-bar/app-bar-more-menu';
import { FrontpageLink } from 'app-bar/links/frontpage-link';
import { AppBarLink } from 'app-bar/links/app-bar-link';
import { PrivilegeRequired } from 'user/privilege-required';
import { PRIV_VIEW_GEOMETRY } from 'user/user-model';

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

    function getNavLink(link: Link) {
        switch (link.link) {
            case '/':
                return <FrontpageLink />;

            case '/infra-model':
                return (
                    <PrivilegeRequired privilege={PRIV_VIEW_GEOMETRY}>
                        <InfraModelLink />
                    </PrivilegeRequired>
                );

            default:
                return <AppBarLink linkAddress={link.link} linkName={t(link.name)} />;
        }
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
                            <li qa-id={link.qaId}>{getNavLink(link)}</li>
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
                <li>
                    <AppBarMoreMenu />
                </li>
            </ul>
        </nav>
    );
};
