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
import { getKmLengths, getKmPost } from 'track-layout/layout-km-post-api';
import { useLoader } from 'utils/react-utils';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { KmPostInfoboxVisibilities } from 'track-layout/track-layout-slice';
import { useKmPostChangeTimes } from 'track-layout/track-layout-react-utils';
import { formatDateShort } from 'utils/date-utils';

type KmPostInfoboxProps = {
    publishType: PublishType;
    kmPostChangeTime: TimeStamp;
    kmPost: LayoutKmPost;
    onShowOnMap: () => void;
    onUnselect: () => void;
    onDataChange: () => void;
    visibilities: KmPostInfoboxVisibilities;
    onVisibilityChange: (visibilities: KmPostInfoboxVisibilities) => void;
};

const KmPostInfobox: React.FC<KmPostInfoboxProps> = ({
    publishType,
    kmPostChangeTime,
    kmPost,
    onShowOnMap,
    onUnselect,
    onDataChange,
    visibilities,
    onVisibilityChange,
}: KmPostInfoboxProps) => {
    const { t } = useTranslation();
    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState<boolean>();
    const updatedKmPost = useLoader(
        () => getKmPost(kmPost.id, publishType),
        [kmPost, kmPostChangeTime, publishType],
    );
    const [kmPostLength, setKmPostLength] = React.useState<number>();
    const changeTimes = useKmPostChangeTimes(kmPost.id);

    React.useEffect(() => {
        getKmLengths(publishType, kmPost.trackNumberId).then((details) => {
            details.filter((value) => value.kmNumber === kmPost.kmNumber)[0]
                ? setKmPostLength(
                      details.filter((value) => value.kmNumber === kmPost.kmNumber)[0].length,
                  )
                : '';
        });
    }, [kmPost]);

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

    const visibilityChange = (key: keyof KmPostInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.km-post.layout.general-title')}
                qa-id="km-post-infobox"
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}>
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
                            <TrackNumberLinkContainer
                                trackNumberId={updatedKmPost?.trackNumberId}
                            />
                        }
                    />

                    <InfoboxField label={'Ratakilometrin pituus'} value={kmPostLength} />

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
                qa-id="layout-km-post-location-infobox"
                contentVisible={visibilities.location}
                onContentVisibilityChange={() => visibilityChange('location')}>
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
            {kmPost.draftType !== 'NEW_DRAFT' && (
                <AssetValidationInfoboxContainer
                    contentVisible={visibilities.validation}
                    onContentVisibilityChange={() => visibilityChange('validation')}
                    id={kmPost.id}
                    type={'KM_POST'}
                    publishType={publishType}
                    changeTime={kmPostChangeTime}
                />
            )}
            <Infobox
                title={t('tool-panel.km-post.layout.change-info-heading')}
                contentVisible={visibilities.log}
                onContentVisibilityChange={() => visibilityChange('log')}>
                <InfoboxContent>
                    {changeTimes && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.created')}
                                value={formatDateShort(changeTimes.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.changed')}
                                value={formatDateShort(changeTimes.changed)}
                            />
                        </React.Fragment>
                    )}
                    {kmPost?.draftType === 'NEW_DRAFT' && (
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
