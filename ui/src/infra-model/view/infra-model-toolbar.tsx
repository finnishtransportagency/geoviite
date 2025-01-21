import * as React from 'react';
import styles from './form/infra-model-form.module.scss';
import { Breadcrumb, BreadcrumbItem } from 'geoviite-design-lib/breadcrumb/breadcrumb';
import { useTranslation } from 'react-i18next';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { useAppNavigate } from 'common/navigate';
import { Menu, menuOption } from 'vayla-design-lib/menu/menu';
import { Item } from 'vayla-design-lib/dropdown/dropdown';

export type FileMenuOption = 'fix-encoding';
export type InfraModelToolbarProps = {
    fileName: string;
    fileMenuItems: Item<FileMenuOption>[];
    fileMenuItemSelected: (item: FileMenuOption) => void;
};

export const InfraModelToolbar: React.FC<InfraModelToolbarProps> = (
    props: InfraModelToolbarProps,
) => {
    const navigate = useAppNavigate();
    const { t } = useTranslation();
    const [fileMenuVisible, setFileMenuVisible] = React.useState(false);
    const fileMenuRef = React.useRef(null);

    const items = props.fileMenuItems.map((item) =>
        menuOption(
            () => {
                props.fileMenuItemSelected(item.value);
            },
            item.name,
            item.qaId,
            item.disabled,
        ),
    );

    return (
        <div className="infra-model-upload__tool-bar">
            <Breadcrumb>
                <BreadcrumbItem onClick={() => navigate('inframodel-list')}>
                    {t('im-form.toolbar.files')}
                </BreadcrumbItem>
                <BreadcrumbItem>{props.fileName}</BreadcrumbItem>
            </Breadcrumb>

            {props.fileMenuItems.length > 0 && (
                <div
                    className={styles['infra-model-upload__title-menu-container']}
                    ref={fileMenuRef}>
                    <Button
                        onClick={() => setFileMenuVisible(!fileMenuVisible)}
                        variant={ButtonVariant.SECONDARY}
                        icon={Icons.More}
                    />
                </div>
            )}

            {fileMenuVisible && (
                <Menu
                    anchorElementRef={fileMenuRef}
                    items={items}
                    onClickOutside={() => setFileMenuVisible(false)}
                    onClose={() => setFileMenuVisible(false)}
                />
            )}
        </div>
    );
};
