import * as React from 'react';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    Menu,
    MenuDividerOption,
    menuDividerOption,
    MenuSelectOption,
    menuSelectOption,
} from 'vayla-design-lib/menu/menu';
import styles from 'app-bar/app-bar.scss';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useNavigate } from 'react-router';
import { useCommonDataAppSelector } from 'store/hooks';
import { postDesiredRole } from 'user/user-api';
import { purgePersistentState } from 'index';
import { Role } from 'user/user-model';

const AppBarMoreMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const [showLogoutConfirmation, setShowLogoutConfirmation] = React.useState(false);
    const menuRef = React.useRef(null);
    const navigate = useNavigate();

    const availableRoles = useCommonDataAppSelector((state) => state.availableRoles);

    const createRoleSelectionOption = (role: Role): MenuSelectOption => {
        return menuSelectOption(
            () => {
                setShowMenu(false);
                postDesiredRole(role.code).then((_roleCode) => {
                    purgePersistentState();
                    navigate('/');
                    location.reload();
                });
            },
            t(`user-roles.${role.code}`),
            `select-role-${role.code}`,
        );
    };

    const createRoleSelectionOptions = (): (MenuSelectOption | MenuDividerOption)[] => {
        return availableRoles.length > 1
            ? [
                  menuDividerOption(),
                  ...availableRoles.map(createRoleSelectionOption),
                  menuDividerOption(),
              ]
            : [];
    };

    const moreActions: (MenuSelectOption | MenuDividerOption)[] = [
        menuSelectOption(
            () => {
                setShowMenu(false);
                navigate('licenses');
            },
            t('app-bar.licenses'),
            'licenses',
        ),
        ...createRoleSelectionOptions(),
        menuSelectOption(
            () => {
                setShowMenu(false);
                setShowLogoutConfirmation(true);
            },
            t('app-bar.logout'),
            'logout',
        ),
    ];

    return (
        <React.Fragment>
            <div
                ref={menuRef}
                title={t('app-bar.more')}
                className={styles['app-bar__link']}
                onClick={() => setShowMenu(!showMenu)}>
                <Icons.Menu color={IconColor.INHERIT} size={IconSize.MEDIUM_SMALL} />
            </div>

            {showMenu && (
                <Menu
                    positionRef={menuRef}
                    items={moreActions}
                    className={styles['app-bar__more-menu']}
                    onClickOutside={() => setShowMenu(false)}
                    opensTowards={'LEFT'}
                />
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
                                {t('button.logout')}
                            </Button>
                        </div>
                    }>
                    {t('app-bar.logout-confirm')}
                </Dialog>
            )}
        </React.Fragment>
    );
};

export default AppBarMoreMenu;
