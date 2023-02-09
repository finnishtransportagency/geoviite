import React from 'react';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import styles from 'tool-panel/switch/switch-infobox.scss';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';
import { getPlanLinkStatus } from 'linking/linking-api';
import { GeometryPlanId } from 'geometry/geometry-model';
import { PublishType, TimeStamp } from 'common/common-model';
import { LayoutSwitchId } from 'track-layout/track-layout-model';
import { Spinner } from 'vayla-design-lib/spinner/spinner';

type LinkingStatusProps = {
    switchId: LayoutSwitchId;
    planId: GeometryPlanId;
    publishType: PublishType;
    switchChangeTime: TimeStamp;
    locationTrackChangeTime: TimeStamp;
};

export const LinkingStatus: React.FC<LinkingStatusProps> = ({
    switchId,
    planId,
    publishType,
    switchChangeTime,
    locationTrackChangeTime,
}) => {
    const { t } = useTranslation();
    const [planStatus, planStatusFetchStatus] = useLoaderWithStatus(
        () => (planId ? getPlanLinkStatus(planId, publishType) : undefined),
        [planId, publishType, switchChangeTime, locationTrackChangeTime],
    );

    const isLinked = planStatus?.switches.find((s) => s.id === switchId)?.isLinked;

    return (
        <InfoboxField
            label={t('tool-panel.switch.geometry.is-linked')}
            className={styles['geometry-switch-infobox__linked-status']}
            value={
                planStatusFetchStatus === LoaderStatus.Ready ? (
                    isLinked ? (
                        <span className={styles['geometry-switch-infobox__linked-text']}>
                            {t('yes')}
                        </span>
                    ) : (
                        <span className={styles['geometry-switch-infobox__not-linked-text']}>
                            {t('no')}
                        </span>
                    )
                ) : (
                    <Spinner />
                )
            }
        />
    );
};
