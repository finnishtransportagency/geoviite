import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Menu, menuDivider, menuOption, MenuOption } from 'vayla-design-lib/menu/menu';
import { useTranslation } from 'react-i18next';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';

type GeometryPlanFilterMenuProps = {
    grouping: GeometryPlanGrouping;
};

export const GeometryPlanFilterMenu: React.FC<GeometryPlanFilterMenuProps> = ({ grouping }) => {
    const { t } = useTranslation();
    const [popupVisible, setPopupVisible] = React.useState(false);
    const ref = React.useRef<HTMLDivElement>(null);

    function togglePopup() {
        setPopupVisible(!popupVisible);
    }

    function closePopup() {
        setPopupVisible(false);
    }

    const menuItems: MenuOption[] = [
        menuOption(
            () => alert('x'),
            t('selection-panel.geometries.group-by-project'),
            'geometry-filter.geometries.group-by-project',
        ),
        menuOption(
            () => alert('x'),
            t('selection-panel.geometries.no-grouping'),
            'geometry-filter.geometries.no-groupping',
        ),
        menuDivider(),
        menuOption(
            () => alert('x'),
            t('selection-panel.geometries.show-paikannuspalvelu-geometries'),
            'geometry-filter.geometries.show-paikannuspalvelu-geometries',
        ),
        menuOption(
            () => alert('x'),
            t('selection-panel.geometries.hide-paikannuspalvelu-geometries'),
            'geometry-filter.geometries.hide-paikannuspalvelu-geometries',
        ),
    ];

    //    const className = createClassName(styles['popup-button']);
    return (
        <React.Fragment>
            <div ref={ref}>
                <Button
                    icon={Icons.Filter}
                    size={ButtonSize.SMALL}
                    variant={ButtonVariant.GHOST}
                    onClick={togglePopup}
                />
                {popupVisible}
            </div>
            {popupVisible && (
                <Menu
                    positionRef={ref}
                    onClickOutside={closePopup}
                    items={menuItems}
                    onClose={closePopup}
                    opensTowards={'LEFT'}
                />
            )}
        </React.Fragment>
    );
};
