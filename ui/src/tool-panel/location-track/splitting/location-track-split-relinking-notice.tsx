import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus } from 'utils/react-utils';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { SwitchRelinkingValidationResult } from 'linking/linking-model';
import { hasUnrelinkableSwitches } from 'tool-panel/location-track/splitting/split-utils';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

type LocationTrackSplitRelinkingNoticeProps = {
    splittingState: SplittingState;
    onClickRelink: () => void;
    switchRelinkingErrors: SwitchRelinkingValidationResult[] | undefined;
    switchRelinkingLoadingState: LoaderStatus;
};

export const LocationTrackSplitRelinkingNotice: React.FC<
    LocationTrackSplitRelinkingNoticeProps
> = ({ onClickRelink, switchRelinkingErrors, switchRelinkingLoadingState }) => {
    const hasCriticalErrors = hasUnrelinkableSwitches(switchRelinkingErrors || []);

    const { t } = useTranslation();
    return (
        <InfoboxContentSpread>
            {switchRelinkingLoadingState === LoaderStatus.Ready &&
                switchRelinkingErrors &&
                switchRelinkingErrors.length > 0 && (
                    <MessageBox type={hasCriticalErrors ? 'ERROR' : 'INFO'}>
                        {hasCriticalErrors
                            ? t('tool-panel.location-track.splitting.relink-critical-errors')
                            : t('tool-panel.location-track.splitting.relink-message')}
                        <div className={styles['location-track-infobox__relink-link']}>
                            <AnchorLink onClick={() => onClickRelink()}>
                                {t('tool-panel.location-track.splitting.cancel-and-relink', {
                                    count: switchRelinkingErrors.length,
                                })}
                            </AnchorLink>
                        </div>
                    </MessageBox>
                )}
            {switchRelinkingLoadingState === LoaderStatus.Loading && (
                <MessageBox>
                    <span className={styles['location-track-infobox__validate-switch-relinking']}>
                        {t('tool-panel.location-track.splitting.validation-in-progress')}
                        <Spinner size={SpinnerSize.SMALL} />
                    </span>
                </MessageBox>
            )}
        </InfoboxContentSpread>
    );
};
