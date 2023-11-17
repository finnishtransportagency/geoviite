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
import { KmPostEditDialogContainer } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import KmPostDeleteConfirmationDialog from 'tool-panel/km-post/dialog/km-post-delete-confirmation-dialog';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { getKmPost, getSingleKmPostKmLength } from 'track-layout/layout-km-post-api';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import { KmPostInfoboxVisibilities } from 'track-layout/track-layout-slice';
import {
    refereshKmPostSelection,
    useKmPostChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { formatDateShort } from 'utils/date-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { roundToPrecision } from 'utils/rounding';
import { ChangeTimes } from 'common/common-slice';

type KmPostInfoboxProps = {
    publishType: PublishType;
    kmPostChangeTime: TimeStamp;
    kmPost: LayoutKmPost;
    onShowOnMap: () => void;
    onSelect: (items: OnSelectOptions) => void;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    onDataChange: () => void;
    visibilities: KmPostInfoboxVisibilities;
    onVisibilityChange: (visibilities: KmPostInfoboxVisibilities) => void;
    changeTimes: ChangeTimes;
};

const KmPostInfobox: React.FC<KmPostInfoboxProps> = ({
    publishType,
    kmPostChangeTime,
    kmPost,
    onShowOnMap,
    onSelect,
    onUnselect,
    onDataChange,
    visibilities,
    onVisibilityChange,
    changeTimes,
}: KmPostInfoboxProps) => {
    const { t } = useTranslation();
    const kmPostCreatedAndChangedTime = useKmPostChangeTimes(kmPost.id);

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const [confirmingDraftDelete, setConfirmingDraftDelete] = React.useState(false);
    const updatedKmPost = useLoader(
        () => getKmPost(kmPost.id, publishType),
        [kmPost.id, kmPostChangeTime, publishType],
    );

    const [kmPostLength, kmPostLengthLoading] = useLoaderWithStatus(
        async () => getSingleKmPostKmLength(publishType, kmPost.id).then((result) => result.length),
        [
            kmPost.id,
            kmPost.state,
            publishType,
            changeTimes.layoutKmPost,
            changeTimes.layoutReferenceLine,
        ],
    );

    function openEditDialog() {
        setShowEditDialog(true);
        onDataChange();
    }

    function closeEditDialog() {
        setShowEditDialog(false);
        onDataChange();
    }

    const visibilityChange = (key: keyof KmPostInfoboxVisibilities) => {
        onVisibilityChange({ ...visibilities, [key]: !visibilities[key] });
    };

    const handleKmPostSave = refereshKmPostSelection('DRAFT', onSelect, onUnselect);

    const kmPostLengthText =
        kmPostLength == undefined
            ? t('tool-panel.km-post.layout.no-kilometer-length')
            : kmPostLength < 0
            ? t('tool-panel.km-post.layout.negative-kilometer-length')
            : `${roundToPrecision(kmPostLength, 3)} m`;

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
                        iconDisabled={publishType === 'OFFICIAL'}
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.track-number')}
                        value={
                            <TrackNumberLinkContainer
                                trackNumberId={updatedKmPost?.trackNumberId}
                            />
                        }
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.state')}
                        value={<LayoutState state={kmPost.state} />}
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.kilometer-length')}
                        value={
                            kmPostLengthLoading === LoaderStatus.Ready ? (
                                kmPostLengthText
                            ) : (
                                <Spinner />
                            )
                        }
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            disabled={!kmPost.location}
                            variant={ButtonVariant.SECONDARY}
                            onClick={onShowOnMap}>
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
                    {kmPostCreatedAndChangedTime && (
                        <React.Fragment>
                            <InfoboxField
                                label={t('tool-panel.created')}
                                value={formatDateShort(kmPostCreatedAndChangedTime.created)}
                            />
                            <InfoboxField
                                label={t('tool-panel.changed')}
                                value={formatDateShort(kmPostCreatedAndChangedTime.changed)}
                            />
                        </React.Fragment>
                    )}
                    {kmPost?.draftType === 'NEW_DRAFT' && (
                        <InfoboxButtons>
                            <Button
                                onClick={() => setConfirmingDraftDelete(true)}
                                icon={Icons.Delete}
                                variant={ButtonVariant.WARNING}
                                size={ButtonSize.SMALL}>
                                {t('button.delete')}
                            </Button>
                        </InfoboxButtons>
                    )}
                </InfoboxContent>
            </Infobox>
            {confirmingDraftDelete && (
                <KmPostDeleteConfirmationDialog
                    id={kmPost.id}
                    onSave={() => handleKmPostSave(kmPost.id)}
                    onClose={() => setConfirmingDraftDelete(false)}
                />
            )}

            {showEditDialog && (
                <KmPostEditDialogContainer
                    kmPostId={kmPost.id}
                    onClose={closeEditDialog}
                    onSave={handleKmPostSave}
                />
            )}
        </React.Fragment>
    );
};

export default KmPostInfobox;
