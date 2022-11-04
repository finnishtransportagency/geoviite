import React from 'react';
import { IconColor, IconComponent, IconSize } from 'vayla-design-lib/icon/Icon';

type InfraModelUploadViewErrorImageProps = {
    icon: IconComponent;
};

const InfraModelUploadViewErrorImage: React.FC<InfraModelUploadViewErrorImageProps> = ({
    icon: Icon,
}: InfraModelUploadViewErrorImageProps) => {
    return <Icon size={IconSize.LARGE} color={IconColor.INHERIT} />;
};

export default InfraModelUploadViewErrorImage;
