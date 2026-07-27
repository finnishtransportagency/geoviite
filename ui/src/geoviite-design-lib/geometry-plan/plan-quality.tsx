import React from 'react';
import { PlanQuality } from 'geometry/geometry-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type PlanQualityProps = {
    quality: PlanQuality | undefined;
};

function getTranslationKey(quality: PlanQuality | undefined) {
    switch (quality) {
        case 'PLAN':
        case 'UNRELIABLE_PLAN':
        case 'UNKNOWN':
            return quality;
        case undefined:
            return 'UNKNOWN';
        default:
            return exhaustiveMatchingGuard(quality);
    }
}

const PlanQualityView: React.FC<PlanQualityProps> = ({ quality }: PlanQualityProps) => {
    const { t } = useTranslation();

    return <React.Fragment>{t(`enum.PlanQuality.${getTranslationKey(quality)}`)}</React.Fragment>;
};

export default PlanQualityView;
