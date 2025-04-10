import * as React from 'react';
import {
    Menu,
    menuDivider,
    MenuDividerOption,
    menuOption,
    MenuSelectOption,
} from 'vayla-design-lib/menu/menu';
import styles from 'app-bar/app-bar.scss';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useNavigate } from 'react-router';
import { useCommonDataAppSelector } from 'store/hooks';
import { postDesiredRole } from 'user/user-api';
import { purgePersistentState } from 'index';
import { Role } from 'user/user-model';
import { Environment, useEnvironmentInfo } from 'environment/environment-info';
import { createDebugActions } from 'app-bar/app-bar-more-menu-debug-actions';

const AppBarMoreMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const menuRef = React.useRef(null);
    const navigate = useNavigate();

    const user = useCommonDataAppSelector((state) => state.user);
    const availableRoles = user?.availableRoles ?? [];

    const environmentInfo = useEnvironmentInfo();

    const createRoleSelectionOption = (role: Role): MenuSelectOption => {
        const roleSelectionDisabled = role.code === user?.role.code;

        const roleOptionTranslation = t(`user-roles.${role.code}`);
        const roleOptionName = roleSelectionDisabled
            ? `${roleOptionTranslation}`
            : t(`user-roles.${role.code}`);

        return menuOption(
            () => {
                postDesiredRole(role.code).then((_roleCode) => {
                    purgePersistentState();
                    navigate('/');
                    location.reload();
                });
            },
            roleOptionName,
            `select-role-${role.code}`,
            roleSelectionDisabled,
        );
    };

    const createRoleSelectionOptions = (): (MenuSelectOption | MenuDividerOption)[] => {
        return availableRoles.length > 1
            ? [menuDivider(), ...availableRoles.map(createRoleSelectionOption), menuDivider()]
            : [];
    };

    const debugActionEnvironments: Environment[] = ['local', 'dev'];
    const debugActions =
        environmentInfo && debugActionEnvironments.includes(environmentInfo.environmentName)
            ? createDebugActions(t)
            : [];

    const moreActions: (MenuSelectOption | MenuDividerOption)[] = [
        menuOption(
            () => {
                navigate('licenses');
            },
            t('app-bar.licenses'),
            'licenses',
        ),
        ...createRoleSelectionOptions(),
        ...debugActions,
    ];

    return (
        <React.Fragment>
            <div
                ref={menuRef}
                title={t('app-bar.more')}
                className={styles['app-bar__link']}
                onClick={() => setShowMenu(!showMenu)}
                qa-id={'show-app-bar-more-menu'}>
                <Icons.Menu color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
            </div>

            {showMenu && (
                <Menu
                    anchorElementRef={menuRef}
                    items={moreActions}
                    className={styles['app-bar__more-menu']}
                    onClickOutside={() => setShowMenu(false)}
                    opensTowards={'LEFT'}
                    qa-id={'app-bar-more-menu'}
                    onClose={() => setShowMenu(false)}
                />
            )}
        </React.Fragment>
    );
};

export default AppBarMoreMenu;
