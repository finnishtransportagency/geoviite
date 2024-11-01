import React from 'react';
import { SwitchHand as SwitchHandModel } from 'common/common-model';
import { useTranslation } from 'react-i18next';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type SwitchHandProps = {
    hand: SwitchHandModel;
};

function getTranslationKey(switchHand: SwitchHandModel) {
    switch (switchHand) {
        case 'LEFT':
        case 'RIGHT':
        case 'SWEDISH_RIGHT':
        case 'NONE':
            return switchHand;
        default:
            return exhaustiveMatchingGuard(switchHand);
    }
}

const SwitchHand: React.FC<SwitchHandProps> = ({ hand }: SwitchHandProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {hand === undefined ? '' : t(`enum.switch-hand.${getTranslationKey(hand)}`)}
        </React.Fragment>
    );
};

export default SwitchHand;
