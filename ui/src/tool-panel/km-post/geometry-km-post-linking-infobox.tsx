import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost, LayoutKmPostId } from 'track-layout/track-layout-model';
import InfoboxContent, { InfoboxContentSpread } from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { getPlanLinkStatus, linkKmPost } from 'linking/linking-api';
import { GeometryKmPostId, GeometryPlanId } from 'geometry/geometry-model';
import { draftLayoutContext, LayoutContext, Srid, TimeStamp } from 'common/common-model';
import { LinkingStatusLabel } from 'geoviite-design-lib/linking-status/linking-status-label';
import { LinkingKmPost } from 'linking/linking-model';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import styles from 'tool-panel/km-post/geometry-km-post-linking-infobox.scss';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { Icons } from 'vayla-design-lib/icon/Icon';
import { KmPostBadge, KmPostBadgeStatus } from 'geoviite-design-lib/km-post/km-post-badge';
import { getKmPostForLinking, getKmPosts } from 'track-layout/layout-km-post-api';
import { TextField, TextFieldVariant } from 'vayla-design-lib/text-field/text-field';
import { KmPostEditDialogContainer } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { OnSelectFunction, OptionalUnselectableItemCollections } from 'selection/selection-model';
import {
    refereshKmPostSelection,
    usePlanHeader,
    useTrackNumbers,
} from 'track-layout/track-layout-react-utils';
import { PrivilegeRequired } from 'user/privilege-required';
import { EDIT_LAYOUT } from 'user/user-model';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators as TrackLayoutActions } from 'track-layout/track-layout-slice';
import { createEmptyItemCollections } from 'selection/selection-store';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { compareByField } from 'utils/array-utils';
import { isFromAnotherGk, isGk } from 'tool-panel/km-post/dialog/km-post-edit-store';
import { GeometryKmPostNonGkSourceWarning } from 'tool-panel/km-post/geometry-km-post-non-gk-source-warning';
import { GeometryKmPostDifferentGkSourceWarning } from 'tool-panel/km-post/geometry-km-post-different-gk-source-warning';
import { MessageBox, MessageBoxType } from 'geoviite-design-lib/message-box/message-box';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

type GeometryKmPostLinkingInfoboxProps = {
    geometryKmPost: LayoutKmPost;
    planId: GeometryPlanId;
    layoutKmPost?: LayoutKmPost;
    kmPostChangeTime: TimeStamp;
    linkingState: LinkingKmPost | undefined;
    startLinking: (geometryKmPostId: GeometryKmPostId) => void;
    stopLinking: () => void;
    onSelect: OnSelectFunction;
    onUnselect: (items: OptionalUnselectableItemCollections) => void;
    layoutContext: LayoutContext;
    contentVisible: boolean;
    onContentVisibilityChange: () => void;
};

function createSelectAction() {
    const delegates = createDelegates(TrackLayoutActions);
    return (kmPostId: LayoutKmPostId) =>
        delegates.onSelect({
            ...createEmptyItemCollections(),
            kmPosts: [kmPostId],
        });
}

type GkSource = 'SAME' | 'DIFFERENT' | 'NOT_GK';

const DifferingGkSourceWarning: React.FC<{ planSrid: Srid; kmPostSrid: Srid }> = ({
    planSrid,
    kmPostSrid,
}) => {
    let gkSource: GkSource;
    if (!isGk(planSrid)) {
        gkSource = 'NOT_GK';
    } else if (isFromAnotherGk(planSrid, kmPostSrid)) {
        gkSource = 'DIFFERENT';
    } else {
        gkSource = 'SAME';
    }

    switch (gkSource) {
        case 'NOT_GK':
            return (
                <InfoboxContentSpread>
                    <MessageBox>
                        <GeometryKmPostNonGkSourceWarning originalSrid={planSrid} />
                    </MessageBox>
                </InfoboxContentSpread>
            );
        case 'DIFFERENT':
            return (
                <InfoboxContentSpread>
                    <MessageBox type={MessageBoxType.GHOST}>
                        <GeometryKmPostDifferentGkSourceWarning originalSrid={planSrid} />
                    </MessageBox>
                </InfoboxContentSpread>
            );
        case 'SAME':
            return <React.Fragment />;
        default:
            return exhaustiveMatchingGuard(gkSource);
    }
};

const GeometryKmPostLinkingInfobox: React.FC<GeometryKmPostLinkingInfoboxProps> = ({
    geometryKmPost,
    layoutKmPost,
    planId,
    kmPostChangeTime,
    linkingState,
    startLinking,
    stopLinking,
    onSelect,
    onUnselect,
    layoutContext,
    contentVisible,
    onContentVisibilityChange,
}: GeometryKmPostLinkingInfoboxProps) => {
    const { t } = useTranslation();
    const [searchTerm, setSearchTerm] = React.useState('');
    const [showAddDialog, setShowAddDialog] = React.useState(false);

    const [fetchedLinkedLayourKmPosts, linkedLayoutKmPostsLoaderStatus] = useLoaderWithStatus(
        () =>
            planId
                ? getPlanLinkStatus(planId, layoutContext).then((planStatus) => {
                      const kmPostIds = planStatus.kmPosts
                          .filter((linkStatus) => linkStatus.id === geometryKmPost.sourceId)
                          .flatMap((linkStatus) => linkStatus.linkedKmPosts);

                      return getKmPosts(kmPostIds, layoutContext).then((posts) =>
                          posts
                              .filter((kp) => kp.state !== 'DELETED')
                              .toSorted((kp1, kp2) =>
                                  compareByField(kp1, kp2, (kp) => kp.kmNumber),
                              ),
                      );
                  })
                : undefined,
        [planId, kmPostChangeTime, layoutContext, geometryKmPost.sourceId],
    );
    const linkedLayoutKmPosts = fetchedLinkedLayourKmPosts ?? [];

    const geometryPlan = usePlanHeader(planId);

    const kmPosts =
        useLoader(async () => {
            return geometryKmPost.layoutLocation
                ? await getKmPostForLinking(
                      layoutContext,
                      geometryKmPost.trackNumberId,
                      geometryKmPost.layoutLocation,
                      0,
                      40,
                  )
                : [];
        }, [
            layoutContext.publicationState,
            layoutContext.branch,
            geometryKmPost.trackNumberId,
            geometryKmPost.layoutLocation,
            layoutKmPost,
        ]) || [];
    const trackNumbers = useTrackNumbers(layoutContext);

    const [linkingCallInProgress, setLinkingCallInProgress] = React.useState(false);
    const canLink = !linkingCallInProgress && linkingState && geometryKmPost && layoutKmPost;

    async function link() {
        if (!canLink) {
            return;
        }
        setLinkingCallInProgress(true);

        try {
            if (linkingState && geometryKmPost && geometryKmPost.sourceId && layoutKmPost) {
                await linkKmPost(layoutContext.branch, {
                    geometryPlanId: planId,
                    geometryKmPostId: geometryKmPost.sourceId,
                    layoutKmPostId: layoutKmPost.id,
                });
                Snackbar.success('tool-panel.km-post.geometry.linking.linking-succeed-msg');
                stopLinking();
            }
        } catch {
            Snackbar.error('error.linking.generic');
        } finally {
            setLinkingCallInProgress(false);
        }
    }

    const handleKmPostSave = refereshKmPostSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    const selectLayoutKmPost = createSelectAction();
    const linkedKmPostsLabel =
        linkedLayoutKmPosts.length > 1
            ? t('tool-panel.km-post.geometry.linking.km-post-label-plural')
            : t('tool-panel.km-post.geometry.linking.km-post-label-singular');

    return (
        <React.Fragment>
            <Infobox
                contentVisible={contentVisible}
                onContentVisibilityChange={onContentVisibilityChange}
                className="geometry-km-post-linking-infobox"
                title={t('tool-panel.km-post.geometry.linking.title')}
                qa-id="geometry-km-post-linking-infobox">
                <InfoboxContent>
                    <InfoboxField
                        qaId="geometry-km-post-linked"
                        label={t('tool-panel.km-post.geometry.linking.is-linked-label')}
                        value={
                            linkedLayoutKmPostsLoaderStatus === LoaderStatus.Ready ? (
                                <LinkingStatusLabel isLinked={linkedLayoutKmPosts.length > 0} />
                            ) : (
                                <Spinner />
                            )
                        }
                    />
                    {linkedLayoutKmPosts.length > 0 && (
                        <InfoboxField
                            label={linkedKmPostsLabel}
                            value={
                                <ol
                                    className={
                                        styles[
                                            'km-post-infobox-linking-infobox__currently-linked-km-posts'
                                        ]
                                    }>
                                    {linkedLayoutKmPosts.map((kmPost) => (
                                        <li key={kmPost.id}>
                                            <KmPostBadge
                                                trackNumber={trackNumbers?.find(
                                                    (tn) => tn.id === kmPost.trackNumberId,
                                                )}
                                                kmPost={kmPost}
                                                status={KmPostBadgeStatus.DEFAULT}
                                                showTrackNumberInBadge={true}
                                                onClick={() => selectLayoutKmPost(kmPost.id)}
                                            />
                                        </li>
                                    ))}
                                </ol>
                            }
                        />
                    )}
                    {!linkingState ? (
                        <PrivilegeRequired privilege={EDIT_LAYOUT}>
                            <InfoboxButtons>
                                <Button
                                    disabled={layoutContext.publicationState !== 'DRAFT'}
                                    title={
                                        layoutContext.publicationState !== 'DRAFT'
                                            ? t(
                                                  'tool-panel.disabled.activity-disabled-in-official-mode',
                                              )
                                            : ''
                                    }
                                    size={ButtonSize.SMALL}
                                    qa-id="start-geometry-km-post-linking"
                                    onClick={() =>
                                        geometryKmPost.sourceId &&
                                        startLinking(geometryKmPost.sourceId)
                                    }>
                                    {t('tool-panel.km-post.geometry.linking.start-linking-command')}
                                </Button>
                            </InfoboxButtons>
                        </PrivilegeRequired>
                    ) : (
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
                                            onClick={() =>
                                                onSelect({ kmPosts: [layoutKmPostOption.id] })
                                            }>
                                            <KmPostBadge
                                                kmPost={layoutKmPostOption}
                                                trackNumber={trackNumbers?.find(
                                                    (tn) =>
                                                        tn.id === layoutKmPostOption.trackNumberId,
                                                )}
                                                showTrackNumberInBadge={true}
                                                status={
                                                    layoutKmPostOption.id === layoutKmPost?.id
                                                        ? KmPostBadgeStatus.SELECTED
                                                        : KmPostBadgeStatus.DEFAULT
                                                }
                                            />
                                        </li>
                                    ))}
                                </ul>
                            </div>
                            {geometryPlan?.units?.coordinateSystemSrid &&
                                geometryKmPost.gkLocation?.location.srid && (
                                    <DifferingGkSourceWarning
                                        planSrid={geometryPlan.units.coordinateSystemSrid}
                                        kmPostSrid={geometryKmPost.gkLocation.location.srid}
                                    />
                                )}

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
                                    qa-id="link-geometry-km-post"
                                    size={ButtonSize.SMALL}>
                                    {t('tool-panel.km-post.geometry.linking.link-command')}
                                </Button>
                            </InfoboxButtons>
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {showAddDialog && (
                <KmPostEditDialogContainer
                    onClose={() => setShowAddDialog(false)}
                    onSave={handleKmPostSave}
                    prefilledTrackNumberId={geometryKmPost.trackNumberId}
                    geometryKmPostGkLocation={geometryKmPost.gkLocation?.location}
                    editType={'LINKING'}
                    geometryPlanSrid={geometryPlan?.units?.coordinateSystemSrid}
                />
            )}
        </React.Fragment>
    );
};

export default GeometryKmPostLinkingInfobox;
