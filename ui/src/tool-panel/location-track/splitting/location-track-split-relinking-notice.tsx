import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import styles from 'tool-panel/location-track/location-track-infobox.scss';
import { Link } from 'vayla-design-lib/link/link';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { getChangeTimes } from 'common/change-time-api';
import { SplittingState } from 'tool-panel/location-track/split-store';
import { LocationTrackId } from 'track-layout/track-layout-model';

type LocationTrackSplitRelinkingNoticeProps = {
    splittingState: SplittingState;
    stopSplitting: () => void;
    onShowTaskList: (id: LocationTrackId) => void;
};

export const LocationTrackSplitRelinkingNotice: React.FC<
    LocationTrackSplitRelinkingNoticeProps
> = ({ splittingState, stopSplitting, onShowTaskList }) => {
    const [switchRelinkingErrors, switchRelinkingState] = useLoaderWithStatus(async () => {
        const validations = await validateLocationTrackSwitchRelinking(
            splittingState.originLocationTrack.id,
        );
        return validations.filter((v) => v.validationErrors.length > 0).map((r) => r.id);
    }, [getChangeTimes().layoutLocationTrack, getChangeTimes().layoutSwitch]);

    const { t } = useTranslation();
    return (
        <InfoboxContentSpread>
            {switchRelinkingState == LoaderStatus.Ready &&
                switchRelinkingErrors &&
                switchRelinkingErrors.length > 0 && (
                    <MessageBox>
                        {t('tool-panel.location-track.splitting.relink-message')}
                        <div className={styles['location-track-infobox__relink-link']}>
                            <Link
                                onClick={() => {
                                    stopSplitting();
                                    onShowTaskList(splittingState.originLocationTrack.id);
                                }}>
                                {t('tool-panel.location-track.splitting.cancel-and-relink', {
                                    count: switchRelinkingErrors.length,
                                })}
                            </Link>
                        </div>
                    </MessageBox>
                )}
            {switchRelinkingState == LoaderStatus.Loading && (
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
