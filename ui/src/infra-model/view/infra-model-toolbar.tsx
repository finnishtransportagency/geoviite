import * as React from 'react';
import { Breadcrumb, BreadcrumbItem } from 'geoviite-design-lib/breadcrumb/breadcrumb';
import { InfraModelViewType } from 'infra-model/infra-model-store';

export type InfraModelToolbarProps = {
    onInfraModelViewChange: (viewType: InfraModelViewType) => void;
    fileName?: string;
    viewType: InfraModelViewType;
};

export const InfraModelToolbar: React.FC<InfraModelToolbarProps> = (
    props: InfraModelToolbarProps,
) => {
    return (
        <div className="infra-model-upload__tool-bar">
            <div className="infra-model-upload__tool-bar-left-section">
                <Breadcrumb>
                    <BreadcrumbItem
                        onClick={() => props.onInfraModelViewChange(InfraModelViewType.LIST)}>
                        IM-tiedostot
                    </BreadcrumbItem>
                    <BreadcrumbItem>
                        {props.viewType === InfraModelViewType.EDIT ? props.fileName : 'Lataa uusi'}
                    </BreadcrumbItem>
                </Breadcrumb>
            </div>

            <div className={'infra-model-upload__tool-bar-middle-section'}></div>
        </div>
    );
};
