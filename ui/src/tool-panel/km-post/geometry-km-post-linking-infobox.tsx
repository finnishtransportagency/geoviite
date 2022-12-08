import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { useDebouncedState, useLoader } from 'utils/react-utils';
import { getPlanLinkStatus, linkKmPost } from 'linking/linking-api';
import { GeometryKmPostId, GeometryPlanId } from 'geometry/geometry-model';
import { PublishType, TimeStamp } from 'common/common-model';
import { LinkingStatusLabel } from 'geoviite-design-lib/linking-status/linking-status-label';
import { LinkingKmPost } from 'linking/linking-model';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from 'tool-panel/km-post/geometry-km-post-linking-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { getKmPost, getKmPostForLinking } from 'track-layout/layout-km-post-api';
import { TextField, TextFieldVariant } from 'vayla-design-lib/text-field/text-field';
import { KmPostEditDialog } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { updateKmPostChangeTime } from 'common/change-time-api';
import { filterNotEmpty } from 'utils/array-utils';

type GeometryKmPostLinkingInfoboxProps = {
    geometryKmPost: LayoutKmPost;
    planId: GeometryPlanId;
    layoutKmPost?: LayoutKmPost;
    kmPostChangeTime: TimeStamp;
    linkingState: LinkingKmPost | undefined;
    startLinking: (geometryKmPostId: GeometryKmPostId) => void;
    stopLinking: () => void;
    onKmPostSelect: (kmPost: LayoutKmPost) => void;
    publishType: PublishType;
};

const GeometryKmPostLinkingInfobox: React.FC<GeometryKmPostLinkingInfoboxProps> = ({
    geometryKmPost,
    layoutKmPost,
    planId,
    kmPostChangeTime,
    linkingState,
    startLinking,
    stopLinking,
    onKmPostSelect,
    publishType,
}: GeometryKmPostLinkingInfoboxProps) => {
    const { t } = useTranslation();
    const [searchTerm, setSearchTerm] = React.useState('');
    const [showAddDialog, setShowAddDialog] = React.useState(false);
    const _debouncedSearchTerm = useDebouncedState(searchTerm, 200);

    const planStatus = useLoader(
        () => (planId ? getPlanLinkStatus(planId, publishType) : undefined),
        [planId, kmPostChangeTime, publishType],
    );

    const linkedLayoutKmPosts = useLoader(() => {
        if (!planStatus) return undefined;
        const kmPostIds = planStatus.kmPosts
            .filter((linkStatus) => linkStatus.id == geometryKmPost.sourceId)
            .flatMap((linkStatus) => linkStatus.linkedKmPosts);
        const kmPostPromises = kmPostIds.map((kmPostId) => getKmPost(kmPostId, publishType));
        return Promise.all(kmPostPromises).then((posts) => posts.filter(filterNotEmpty));
    }, [planStatus]);

    const kmPosts =
        useLoader(async () => {
            return geometryKmPost.location
                ? await getKmPostForLinking(
                      publishType,
                      geometryKmPost.trackNumberId,
                      geometryKmPost.location,
                      0,
                      40,
                  )
                : [];
        }, [geometryKmPost.trackNumberId, geometryKmPost.location, layoutKmPost]) || [];

    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const canLink = !linkingCallInProgress && linkingState && geometryKmPost && layoutKmPost;

    async function link() {
        if (!canLink) {
            return;
        }
        setLinkingCallInProgress(true);

        try {
            if (linkingState && geometryKmPost && geometryKmPost.sourceId && layoutKmPost) {
                await linkKmPost(geometryKmPost.sourceId, layoutKmPost.id);
                Snackbar.success(t('tool-panel.km-post.geometry.linking.linking-succeed-msg'));
                stopLinking();
            }
        } catch {
            Snackbar.error(t('error.linking.generic'));
        } finally {
            setLinkingCallInProgress(false);
        }
    }

    function handleKmPostInsert(id: LayoutKmPostId) {
        updateKmPostChangeTime().then((kp) => {
            getKmPost(id, publishType, kp).then((kmPost) => {
                if (kmPost) onKmPostSelect(kmPost);
            });
            setShowAddDialog(false);
        });

        setShowAddDialog(false);
    }

    return (
        <React.Fragment>
            <Infobox
                className={styles['geometry-km-post-linking-infobox']}
                title={t('tool-panel.km-post.geometry.linking.title')}
                qa-id="geometry-km-post-linking-infobox">
                <InfoboxContent>
                    <InfoboxField
                        label={t('tool-panel.km-post.geometry.linking.is-linked-label')}
                        value={
                            linkedLayoutKmPosts != undefined && (
                                <LinkingStatusLabel isLinked={linkedLayoutKmPosts?.length > 0} />
                            )
                        }
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.geometry.linking.km-post-label')}
                        value={
                            linkedLayoutKmPosts &&
                            linkedLayoutKmPosts.map((kmPost) => (
                                <KmPostBadge
                                    key={kmPost.id}
                                    kmPost={kmPost}
                                    status={KmPostBadgeStatus.DEFAULT}
                                />
                            ))
                        }
                    />
                    {!linkingState && (
                        <InfoboxButtons>
                            <Button
                                size={ButtonSize.SMALL}
                                onClick={() => startLinking(geometryKmPost.id)}>
                                {t('tool-panel.km-post.geometry.linking.start-linking-command')}
                            </Button>
                        </InfoboxButtons>
                    )}

                    {linkingState && (
                        <React.Fragment>
                            <div
                                className={
                                    styles[
                                        'geometry-km-post-linking-infobox__layout-km-post-selection'
                                    ]
                                }>
                                <InfoboxText
                                    value={t(
                                        'tool-panel.km-post.geometry.linking.choose-km-post-msg',
                                    )}
                                />
                                <div className={styles['geometry-km-post-linking-infobox__search']}>
                                    <div
                                        className={
                                            styles['geometry-km-post-linking-infobox__search-input']
                                        }>
                                        <TextField
                                            variant={TextFieldVariant.NO_BORDER}
                                            Icon={Icons.Search}
                                            wide
                                            value={searchTerm}
                                            onChange={(e) => {
                                                setSearchTerm(e.target.value);
                                            }}
                                        />
                                    </div>

                                    <Button
                                        variant={ButtonVariant.GHOST}
                                        size={ButtonSize.SMALL}
                                        icon={Icons.Append}
                                        onClick={() => setShowAddDialog(true)}
                                    />
                                </div>
                                <ul
                                    className={
                                        styles['geometry-km-post-linking-infobox__layout-km-posts']
                                    }>
                                    {kmPosts.map((layoutKmPostOption) => (
                                        <li
                                            key={layoutKmPostOption.id}
                                            className={
                                                styles[
                                                    'geometry-km-post-linking-infobox__layout-km-post'
                                                ]
                                            }
                                            onClick={() => onKmPostSelect(layoutKmPostOption)}>
                                            <KmPostBadge
                                                kmPost={layoutKmPostOption}
                                                status={
                                                    layoutKmPostOption.id == layoutKmPost?.id
                                                        ? KmPostBadgeStatus.SELECTED
                                                        : KmPostBadgeStatus.DEFAULT
                                                }
                                            />
                                        </li>
                                    ))}
                                </ul>
                            </div>

                            <InfoboxButtons>
                                <Button
                                    onClick={() => stopLinking()}
                                    disabled={linkingCallInProgress}
                                    variant={ButtonVariant.SECONDARY}
                                    size={ButtonSize.SMALL}>
                                    {t('button.cancel')}
                                </Button>
                                <Button
                                    onClick={() => link()}
                                    isProcessing={linkingCallInProgress}
                                    disabled={!canLink}
                                    size={ButtonSize.SMALL}>
                                    {t('tool-panel.km-post.geometry.linking.link-command')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {showAddDialog && (
                <KmPostEditDialog
                    onClose={() => setShowAddDialog(false)}
                    onInsert={handleKmPostInsert}
                    prefilledTrackNumberId={geometryKmPost.trackNumberId}
                />
            )}
        </React.Fragment>
    );
};

export default GeometryKmPostLinkingInfobox;
