import * as React from 'react';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import styles from 'tool-panel/switch/switch-infobox.scss';
import { Checkbox } from 'vayla-design-lib/checkbox/checkbox';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { Trans, useTranslation } from 'react-i18next';
import { SwitchStructure } from 'common/common-model';
import { SwitchTypeMatch } from 'linking/linking-utils';

type GeometrySwitchLinkingErrorsProps = {
    selectedLayoutSwitchStructure: SwitchStructure | undefined;
    switchTypeMatch: SwitchTypeMatch;
    suggestedSwitchStructure: SwitchStructure;
    onConfirmChanged: (confirmed: boolean) => void;
};

export const GeometrySwitchLinkingErrors: React.FC<GeometrySwitchLinkingErrorsProps> = ({
    selectedLayoutSwitchStructure,
    suggestedSwitchStructure,
    switchTypeMatch,
    onConfirmChanged,
}) => {
    const { t } = useTranslation();
    const [typeDifferenceConfirmed, setTypeDifferenceConfirmed] = React.useState(false);

    return (
        <InfoboxContentSpread>
            <MessageBox
                type={MessageBoxType.ERROR}
                pop={
                    selectedLayoutSwitchStructure !== undefined &&
                    switchTypeMatch === SwitchTypeMatch.Invalid
                }>
                <div className={styles['geometry-switch-infobox__switch-type-warning-msg']}>
                    <Trans
                        i18nKey="tool-panel.switch.geometry.cannot-link-invalid-switch-type"
                        values={{
                            suggestedType: suggestedSwitchStructure.type,
                            selectedType: selectedLayoutSwitchStructure?.type,
                        }}
                    />
                </div>
            </MessageBox>
            <MessageBox
                pop={
                    selectedLayoutSwitchStructure !== undefined &&
                    switchTypeMatch === SwitchTypeMatch.Similar
                }>
                <div className={styles['geometry-switch-infobox__switch-type-warning-msg']}>
                    <Trans
                        i18nKey={'tool-panel.switch.geometry.switch-type-differs-warning'}
                        values={{
                            suggestedType: suggestedSwitchStructure.type,
                            selectedType: selectedLayoutSwitchStructure?.type,
                        }}
                    />
                </div>
                <div className={styles['geometry-switch-infobox__switch-type-confirm']}>
                    <Checkbox
                        checked={typeDifferenceConfirmed}
                        onChange={(e) => {
                            setTypeDifferenceConfirmed(e.target.checked);
                            onConfirmChanged(e.target.checked);
                        }}>
                        {t('tool-panel.switch.geometry.switch-type-confirm-msg')}
                    </Checkbox>
                </div>
            </MessageBox>
        </InfoboxContentSpread>
    );
};
