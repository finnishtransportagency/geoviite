import React from 'react';
import { MeasurementMethod as MeasurementMethodModel } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type MeasurementMethodProps = {
    method: MeasurementMethodModel | undefined;
};

function getTranslationKey(method: MeasurementMethodModel | undefined) {
    switch (method) {
        case 'VERIFIED_DESIGNED_GEOMETRY':
        case 'OFFICIALLY_MEASURED_GEODETICALLY':
        case 'TRACK_INSPECTION':
        case 'DIGITIZED_AERIAL_IMAGE':
        case 'UNVERIFIED_DESIGNED_GEOMETRY':
            return method;
        case undefined:
            return 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(method);
    }
}

const MeasurementMethod: React.FC<MeasurementMethodProps> = ({
    method,
}: MeasurementMethodProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>{t(`enum.MeasurementMethod.${getTranslationKey(method)}`)}</React.Fragment>
    );
};

export default MeasurementMethod;
