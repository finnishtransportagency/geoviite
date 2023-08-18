import React from 'react';
import { ElevationMeasurementMethod } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type ElevationMeasurementMethodProps = {
    method: ElevationMeasurementMethod | null;
    lowerCase?: boolean;
    includeTermContextForUnknownMethod?: boolean;
};

function getTranslationKey(
    method: ElevationMeasurementMethod | null,
    includeTermContextForUnknownMethod: boolean,
) {
    switch (method) {
        case 'TOP_OF_SLEEPER':
        case 'TOP_OF_RAIL':
            return method;
        case null:
            return includeTermContextForUnknownMethod ? 'UNKNOWN-WITH-TERM-CONTEXT' : 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(method);
    }
}

const ElevationMeasurementMethod: React.FC<ElevationMeasurementMethodProps> = ({
    method,
    lowerCase = false,
    includeTermContextForUnknownMethod = false,
}: ElevationMeasurementMethodProps) => {
    const { t } = useTranslation();

    const measurementMethodTranslationKey = getTranslationKey(
        method,
        includeTermContextForUnknownMethod,
    );

    const text = lowerCase
        ? t(`enum.elevation-measurement-method.${measurementMethodTranslationKey}`).toLowerCase()
        : t(`enum.elevation-measurement-method.${measurementMethodTranslationKey}`);

    return <React.Fragment>{text}</React.Fragment>;
};

export default ElevationMeasurementMethod;
