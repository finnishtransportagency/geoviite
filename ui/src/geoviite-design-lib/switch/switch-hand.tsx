import React from 'react';
import { SwitchHand as SwitchHandModel } from 'common/common-model';
import { useTranslation } from 'react-i18next';

type SwitchHandProps = {
    hand?: SwitchHandModel | null;
};

function getTranslationKey(switchHand: SwitchHandModel | null) {
    switch (switchHand) {
        case 'LEFT':
        case 'RIGHT':
        case 'NONE':
            return switchHand;
        default:
            return 'UNKNOWN';
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
