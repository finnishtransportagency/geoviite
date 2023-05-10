import * as React from 'react';
import styles from './form/infra-model-form.module.scss';
import { Breadcrumb, BreadcrumbItem } from 'geoviite-design-lib/breadcrumb/breadcrumb';
import { useTranslation } from 'react-i18next';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { Menu, Item } from 'vayla-design-lib/menu/menu';

export type FileMenuOption = 'fix-encoding';
export type InfraModelToolbarProps = {
    navigateToList: () => void;
    fileName: string;
    fileMenuItems: Item<FileMenuOption>[];
    fileMenuItemSelected: (item: FileMenuOption) => void;
};

export const InfraModelToolbar: React.FC<InfraModelToolbarProps> = (
    props: InfraModelToolbarProps,
) => {
    const { t } = useTranslation();
    const [fileMenuVisible, setFileMenuVisible] = React.useState(false);

    return (
        <div className="infra-model-upload__tool-bar">
            <Breadcrumb>
                <BreadcrumbItem onClick={props.navigateToList}>
                    {t('im-form.toolbar.files')}
                </BreadcrumbItem>
                <BreadcrumbItem>{props.fileName}</BreadcrumbItem>
            </Breadcrumb>

            {props.fileMenuItems.length > 0 && (
                <div className={styles['infra-model-upload__title-menu-container']}>
                    <Button
                        onClick={() => setFileMenuVisible(!fileMenuVisible)}
                        variant={ButtonVariant.SECONDARY}
                        icon={Icons.More}
                    />
                    {fileMenuVisible && (
                        <div className={styles['infra-model-upload__title-menu']}>
                            <Menu
                                items={props.fileMenuItems}
                                onChange={(item) => {
                                    item && props.fileMenuItemSelected(item);
                                    setFileMenuVisible(false);
                                }}></Menu>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};
