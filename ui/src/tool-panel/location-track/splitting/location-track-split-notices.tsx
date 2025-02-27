import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

export const LocationTrackSplittingErrorNotice: React.FC<{
    msg: string;
}> = ({ msg }) => {
    return (
        <InfoboxContentSpread>
            <MessageBox type={'ERROR'}>{msg}</MessageBox>
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
    draftDuplicateName: string;
}> = ({ draftDuplicateName }) => {
    const { t } = useTranslation();
    return (
        <InfoboxContentSpread>
            <MessageBox type={'ERROR'}>
                <div>
                    {t('tool-panel.location-track.splitting.validation.duplicate-not-published', {
                        duplicateName: draftDuplicateName,
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
