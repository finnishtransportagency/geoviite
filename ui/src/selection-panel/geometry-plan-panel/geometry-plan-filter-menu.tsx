import * as React from 'react';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Menu, menuDivider, menuOption, MenuOption } from 'vayla-design-lib/menu/menu';
import { useTranslation } from 'react-i18next';
import { GeometryPlanGrouping } from 'track-layout/track-layout-slice';
import { PlanSource } from 'geometry/geometry-model';

type GeometryPlanFilterMenuProps = {
    grouping: GeometryPlanGrouping;
    onGroupingChanged: (grouping: GeometryPlanGrouping) => void;
    visibleSources: PlanSource[];
    onVisibleSourcesChanged: (sources: PlanSource[]) => void;
};

export const GeometryPlanFilterMenu: React.FC<GeometryPlanFilterMenuProps> = ({
    grouping,
    onGroupingChanged,
    visibleSources,
    onVisibleSourcesChanged,
}) => {
    const { t } = useTranslation();
    const [popupVisible, setPopupVisible] = React.useState(false);
    const ref = React.useRef<HTMLDivElement>(null);

    function togglePopup() {
        setPopupVisible(!popupVisible);
    }

    function closePopup() {
        setPopupVisible(false);
    }

    function toggleVisibleSources() {
        const newSources: PlanSource[] = visibleSources.includes('PAIKANNUSPALVELU')
            ? ['GEOMETRIAPALVELU']
            : ['GEOMETRIAPALVELU', 'PAIKANNUSPALVELU'];
        onVisibleSourcesChanged(newSources);
    }

    const showPlansOfPaikannuspalvelu = visibleSources.includes('PAIKANNUSPALVELU');
    const menuItems: MenuOption[] = [
        {
            ...menuOption(
                () => onGroupingChanged(GeometryPlanGrouping.ByProject),
                t('selection-panel.geometries.group-by-project'),
                'geometry-filter.geometries.group-by-project',
            ),
            icon: grouping === GeometryPlanGrouping.ByProject ? Icons.Tick : undefined,
        },
        {
            ...menuOption(
                () => onGroupingChanged(GeometryPlanGrouping.None),
                t('selection-panel.geometries.no-grouping'),
                'geometry-filter.geometries.no-groupping',
            ),
            icon: grouping === GeometryPlanGrouping.None ? Icons.Tick : undefined,
        },
        menuDivider(),
        {
            ...menuOption(
                toggleVisibleSources,
                t('selection-panel.geometries.show-paikannuspalvelu-geometries'),
                'geometry-filter.geometries.show-paikannuspalvelu-geometries',
            ),
            icon: showPlansOfPaikannuspalvelu ? Icons.Tick : undefined,
        },
    ];

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
                />
            )}
        </React.Fragment>
    );
};
