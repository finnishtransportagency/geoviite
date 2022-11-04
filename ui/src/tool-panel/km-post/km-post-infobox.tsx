import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { formatToTM35FINString } from 'utils/geography-utils';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { PublishType, TimeStamp } from 'common/common-model';
import { KmPostEditDialog } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import KmPostDeleteConfirmationDialog from 'tool-panel/km-post/dialog/km-post-delete-confirmation-dialog';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { getKmPost, officialKmPostExists } from 'track-layout/track-layout-api';
import { useLoader } from 'utils/react-utils';
import { TrackNumberLink } from 'geoviite-design-lib/track-number/track-number-link';

type KmPostInfoboxProps = {
    publishType: PublishType;
    kmPostChangeTime: TimeStamp;
    kmPost: LayoutKmPost;
    onShowOnMap: () => void;
    onUnselect: () => void;
    onDataChange: () => void;
};

const KmPostInfobox: React.FC<KmPostInfoboxProps> = ({
    publishType,
    kmPostChangeTime,
    kmPost,
    onShowOnMap,
    onUnselect,
    onDataChange,
}: KmPostInfoboxProps) => {
    const { t } = useTranslation();
    const [officialKmPost, setOfficialKmPost] = React.useState<LayoutKmPost>();
    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const updatedKmPost = useLoader(
        () => getKmPost(kmPost.id, publishType),
        [kmPost, kmPostChangeTime, publishType],
    );

    React.useEffect(() => {
        if (kmPost.id) {
            officialKmPostExists(kmPost.id).then((exists) => {
                if (exists) {
                    getKmPost(kmPost.id, 'OFFICIAL').then((kmPost) => setOfficialKmPost(kmPost));
                }
            });
        }
    }, [kmPost.id, kmPostChangeTime]);

    function isOfficial(): boolean {
        return publishType === 'OFFICIAL';
    }

    function openEditDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function closeEditDialog() {
        setShowEditDialog(false);
        onDataChange();
    }

    const showDeleteConfirmation = () => {
        setConfirmingDraftDelete(true);
    };

    const closeDeleteConfirmation = () => {
        setConfirmingDraftDelete(false);
    };

    const closeConfirmationAndUnselect = () => {
        closeDeleteConfirmation();
        onUnselect();
    };

    return (
        <React.Fragment>
            <Infobox title={t('tool-panel.km-post.layout.general-title')} qa-id="km-post-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.km-post')}
                        value={updatedKmPost?.kmNumber}
                        onEdit={openEditDialog}
                        iconDisabled={isOfficial()}
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.track-number')}
                        value={
                            <TrackNumberLink
                                trackNumberId={updatedKmPost?.trackNumberId}
                                publishType={publishType}
                            />
                        }
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            disabled={!kmPost.location}
                            variant={ButtonVariant.SECONDARY}
                            onClick={() => onShowOnMap()}>
                            {t('tool-panel.km-post.layout.show-on-map')}
                        </Button>
                    </InfoboxButtons>
                </InfoboxContent>
            </Infobox>
            <Infobox
                title={t('tool-panel.km-post.layout.location-title')}
                qa-id="layout-km-post-location-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.location')}
                        value={kmPost.kmNumber}
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.coordinates')}
                        value={
                            updatedKmPost?.location
                                ? formatToTM35FINString(updatedKmPost.location)
                                : '-'
                        }
                    />
                </InfoboxContent>
            </Infobox>
            <Infobox title={t('tool-panel.km-post.layout.change-info-heading')}>
                <InfoboxContent>
                    {officialKmPost === undefined && (
                        <InfoboxButtons>
                            <Button
                                onClick={() => showDeleteConfirmation()}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}
                                size={ButtonSize.SMALL}>
                                {t('tool-panel.km-post.layout.delete-draft')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>
            {confirmingDraftDelete && (
                <KmPostDeleteConfirmationDialog
                    id={kmPost.id}
                    onClose={closeConfirmationAndUnselect}
                    onCancel={closeDeleteConfirmation}
                />
            )}

            {showEditDialog && (
                <KmPostEditDialog
                    kmPostId={kmPost.id}
                    onClose={closeEditDialog}
                    onUpdate={closeEditDialog}
                    onUnselect={onUnselect}
                />
            )}
        </React.Fragment>
    );
};

export default KmPostInfobox;
