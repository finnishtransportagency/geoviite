import * as React from 'react';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { pushToRatko } from 'ratko/ratko-api';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import { WriteAccessRequired } from 'user/write-access-required';

type RatkoPublishButtonProps = {
    size?: ButtonSize;
    disabled?: boolean;
};

const RatkoPublishButton: React.FC<RatkoPublishButtonProps> = ({ size, disabled }) => {
    const { t } = useTranslation();
    const [isPublishing, setIsPublishing] = React.useState(false);
    const [showingConfirmation, setShowingConfirmation] = React.useState(false);
    const publishToRatko = () => {
        setShowingConfirmation(false);
        setIsPublishing(true);
        // TODO Catch cases where RatkoAPI is not online
        pushToRatko().finally(() => setIsPublishing(false));
    };

    return (
        <React.Fragment>
            <WriteAccessRequired>
                <Button
                    onClick={() => setShowingConfirmation(true)}
                    disabled={isPublishing || disabled}
                    isProcessing={isPublishing}
                    variant={ButtonVariant.PRIMARY}
                    size={size}
                    icon={Icons.Redo}
                    qa-id="publish-to-ratko">
                    {t('publishing.publish-to-ratko')}
                </Button>
            </WriteAccessRequired>
            {showingConfirmation && (
                <Dialog
                    title={t('publishing.publish-to-ratko')}
                    allowClose={false}
                    footerContent={
                        <div className={dialogStyles['dialog__footer-content--centered']}>
                            <Button
                                onClick={() => setShowingConfirmation(false)}
                                variant={ButtonVariant.SECONDARY}>
                                {t('button.cancel')}
                            </Button>
                            <Button
                                qa-id="confirm-publish-to-ratko"
                                onClick={publishToRatko}
                                variant={ButtonVariant.PRIMARY}>
                                {t('publishing.publish-to-ratko')}
                            </Button>
                        </div>
                    }>
                    {t('publishing.publish-confirmation')}
                </Dialog>
            )}
        </React.Fragment>
    );
};

export default RatkoPublishButton;
