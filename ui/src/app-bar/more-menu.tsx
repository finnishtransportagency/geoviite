import * as React from 'react';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Menu } from 'vayla-design-lib/menu/menu';
import styles from 'app-bar/app-bar.scss';
import { useTranslation } from 'react-i18next';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { useNavigate } from 'react-router';

const MoreMenu: React.FC = () => {
    const { t } = useTranslation();
    const [showMenu, setShowMenu] = React.useState(false);
    const [showLogoutConfirmation, setShowLogoutConfirmation] = React.useState(false);
    const menuRef = React.useRef(null);
    const navigate = useNavigate();

    const moreActions = [
        {
            onSelect: () => {
                setShowMenu(false);
                navigate('licenses');
            },
            name: t('app-bar.licenses'),
        },
        {
            onSelect: () => {
                setShowMenu(false);
                setShowLogoutConfirmation(true);
            },
            name: t('app-bar.logout'),
        },
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
                                {t('button.ok')}
                            </Button>
                        </div>
                    }>
                    {t('app-bar.logout-confirm')}
                </Dialog>
            )}
        </React.Fragment>
    );
};

export default MoreMenu;