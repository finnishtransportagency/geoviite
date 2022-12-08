import * as React from 'react';
import { Breadcrumb, BreadcrumbItem } from 'geoviite-design-lib/breadcrumb/breadcrumb';
import { InfraModelViewType } from 'infra-model/infra-model-store';
import { useTranslation } from 'react-i18next';

export type InfraModelToolbarProps = {
    onInfraModelViewChange: (viewType: InfraModelViewType) => void;
    fileName?: string;
    viewType: InfraModelViewType;
};

export const InfraModelToolbar: React.FC<InfraModelToolbarProps> = (
    props: InfraModelToolbarProps,
) => {
    const { t } = useTranslation();

    return (
        <div className="infra-model-upload__tool-bar">
            <Breadcrumb>
                <BreadcrumbItem
                    onClick={() => props.onInfraModelViewChange(InfraModelViewType.LIST)}>
                    {t('im-form.toolbar.files')}
                </BreadcrumbItem>
                <BreadcrumbItem>
                    {props.viewType === InfraModelViewType.EDIT
                        ? props.fileName
                        : t('im-form.toolbar.upload')}
                </BreadcrumbItem>
            </Breadcrumb>
        </div>
    );
};
