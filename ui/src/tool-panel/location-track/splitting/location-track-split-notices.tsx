import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { useLocationTrackName } from 'track-layout/track-layout-react-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { LayoutContext } from 'common/common-model';
import { getChangeTimes } from 'common/change-time-api';

export const LocationTrackSplittingErrorNotice: React.FC<{
    msg: string;
}> = ({ msg }) => {
    return (
        <InfoboxContentSpread>
            <MessageBox type={MessageBoxType.ERROR}>{msg}</MessageBox>
        </InfoboxContentSpread>
    );
};

export const LocationTrackSplittingDraftExistsErrorNotice: React.FC = () => {
    const { t } = useTranslation();
    return (
        <LocationTrackSplittingErrorNotice
            msg={t('tool-panel.location-track.splitting.validation.track-draft-exists')}
        />
    );
};

export const LocationTrackSplittingGuideNotice: React.FC = () => {
    const { t } = useTranslation();
    return (
        <InfoboxContentSpread>
            <MessageBox>{t('tool-panel.location-track.splitting.splitting-guide')}</MessageBox>
        </InfoboxContentSpread>
    );
};

export const LocationTrackSplittingDuplicateTrackNotPublishedErrorNotice: React.FC<{
    draftDuplicateLocationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
}> = ({ draftDuplicateLocationTrackId, layoutContext }) => {
    const { t } = useTranslation();
    const duplicateName = useLocationTrackName(
        draftDuplicateLocationTrackId,
        layoutContext,
        getChangeTimes(),
    );
    return (
        <InfoboxContentSpread>
            <MessageBox type={MessageBoxType.ERROR}>
                <div>
                    {t('tool-panel.location-track.splitting.validation.duplicate-not-published', {
                        duplicateName: duplicateName?.name,
                    })}
                </div>
                <br />
                <div>{t('tool-panel.location-track.splitting.validation.publish-duplicate')}</div>
            </MessageBox>
        </InfoboxContentSpread>
    );
};

type NoticeWithNavigationLinkParams = {
    noticeLocalizationKey: string;
    onClickLink: () => void;
};

export const NoticeWithNavigationLink: React.FC<NoticeWithNavigationLinkParams> = ({
    noticeLocalizationKey,
    onClickLink,
}) => {
    const { t } = useTranslation();
    return (
        <InfoboxContentSpread>
            <MessageBox>
                {t(noticeLocalizationKey)},{' '}
                <AnchorLink onClick={onClickLink}>
                    {t('tool-panel.location-track.splitting.validation.show')}
                </AnchorLink>
            </MessageBox>
        </InfoboxContentSpread>
    );
};
