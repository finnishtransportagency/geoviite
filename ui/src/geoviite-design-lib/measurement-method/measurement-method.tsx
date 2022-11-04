import React from 'react';
import { MeasurementMethod as MeasurementMethodModel } from 'common/common-model';
import { useTranslation } from 'react-i18next';

type MeasurementMethodProps = {
    method: MeasurementMethodModel | null;
};

function getTranslationKey(method: MeasurementMethodModel | null) {
    switch (method) {
        case 'VERIFIED_DESIGNED_GEOMETRY':
        case 'OFFICIALLY_MEASURED_GEODETICALLY':
        case 'TRACK_INSPECTION':
        case 'DIGITIZED_AERIAL_IMAGE':
        case 'UNVERIFIED_DESIGNED_GEOMETRY':
            return method;
        default:
            return 'UNKNOWN';
    }
}

const MeasurementMethod: React.FC<MeasurementMethodProps> = ({
    method,
}: MeasurementMethodProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>{t(`enum.measurement-method.${getTranslationKey(method)}`)}</React.Fragment>
    );
};

export default MeasurementMethod;
