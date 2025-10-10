import * as React from 'react';
import Infobox from 'tool-panel/infobox/infobox';
import { LayoutKmPost } from 'track-layout/track-layout-model';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import { useTranslation } from 'react-i18next';
import { formatToGkFinString, formatToTM35FINString, formatWithSrid } from 'utils/geography-utils';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import {
    CoordinateSystem,
    draftLayoutContext,
    LayoutContext,
    TimeStamp,
} from 'common/common-model';
import { KmPostEditDialogContainer } from 'tool-panel/km-post/dialog/km-post-edit-dialog';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { getKmPost, getKmPostInfoboxExtras } from 'track-layout/layout-km-post-api';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import { AssetValidationInfoboxContainer } from 'tool-panel/asset-validation-infobox-container';
import {
    KmPostInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import {
    refereshKmPostSelection,
    useCoordinateSystem,
    useKmPostChangeTimes,
} from 'track-layout/track-layout-react-utils';
import { formatDateShort } from 'utils/date-utils';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import { OnSelectOptions, OptionalUnselectableItemCollections } from 'selection/selection-model';
import LayoutState from 'geoviite-design-lib/layout-state/layout-state';
import { roundToPrecision } from 'utils/rounding';
import { ChangeTimes } from 'common/common-slice';
import { createDelegates } from 'store/store-utils';
import { getGeometryPlan } from 'geometry/geometry-api';
import CoordinateSystemView from 'geoviite-design-lib/coordinate-system/coordinate-system-view';
import styles from './km-post-infobox.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { GK_FIN_COORDINATE_SYSTEMS } from 'tool-panel/km-post/dialog/km-post-edit-store';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';
import { SearchItemType } from 'asset-search/search-dropdown';
import { useAppNavigate } from 'common/navigate';
import { useHasPublicationLog } from 'publication/publication-utils';

type KmPostInfoboxProps = {
    layoutContext: LayoutContext;
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

const CoordinateSystemField: React.FC<{
    kmPostCrs: CoordinateSystem | undefined;
    planCrs: CoordinateSystem | undefined;
}> = ({ kmPostCrs, planCrs }) => {
    const { t } = useTranslation();

    const transformed = planCrs && kmPostCrs && planCrs !== kmPostCrs;
    const transformedFromNonGk =
        planCrs && !GK_FIN_COORDINATE_SYSTEMS.find(([srid]) => srid === planCrs?.srid);
    const titleIftransformed = t('km-post-dialog.gk-location.location-converted', {
        originalCrs: planCrs ? formatWithSrid(planCrs) : '',
    });

    const classNme = createClassName(
        styles['km-post-infobox__crs'],
        transformedFromNonGk && styles['km-post-infobox__crs--warning'],
    );

    return (
        <span title={transformed ? titleIftransformed : ''} className={classNme}>
            <CoordinateSystemView coordinateSystem={kmPostCrs} />

            {transformed &&
                (transformedFromNonGk ? (
                    <Icons.StatusError size={IconSize.SMALL} color={IconColor.INHERIT} />
                ) : (
                    <Icons.Info size={IconSize.SMALL} color={IconColor.INHERIT} />
                ))}
        </span>
    );
};

const KmPostInfobox: React.FC<KmPostInfoboxProps> = ({
    layoutContext,
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
    const navigate = useAppNavigate();
    const kmPostCreatedAndChangedTime = useKmPostChangeTimes(kmPost.id, layoutContext);

    const [showEditDialog, setShowEditDialog] = React.useState(false);
    const updatedKmPost = useLoader(
        () => getKmPost(kmPost.id, layoutContext),
        [kmPost.id, kmPostChangeTime, layoutContext.publicationState, layoutContext.branch],
    );

    const [infoboxExtras, infoboxExtrasLoading] = useLoaderWithStatus(
        async () => getKmPostInfoboxExtras(layoutContext, kmPost.id),
        [
            kmPost.id,
            kmPost.state,
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.layoutKmPost,
            changeTimes.layoutReferenceLine,
        ],
    );
    const geometryPlan = useLoader(
        () =>
            infoboxExtras?.sourceGeometryPlanId === undefined
                ? undefined
                : getGeometryPlan(infoboxExtras.sourceGeometryPlanId),
        [infoboxExtras?.sourceGeometryPlanId],
    );
    const geometryPlanCrs = useCoordinateSystem(geometryPlan?.units?.coordinateSystemSrid);

    const gkCoordinateSystem = useCoordinateSystem(kmPost.gkLocation?.location?.srid);

    const delegates = React.useMemo(() => createDelegates(TrackLayoutActions), []);

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

    const handleKmPostSave = refereshKmPostSelection(
        draftLayoutContext(layoutContext),
        onSelect,
        onUnselect,
    );

    const hasPublicationLog = useHasPublicationLog(
        kmPost.id,
        getKmPost,
        kmPostCreatedAndChangedTime?.changed,
    );

    const openPublicationLogButtonTitle = hasPublicationLog
        ? undefined
        : t('tool-panel.km-post.layout.publication-log-unavailable');

    const openPublicationLog = React.useCallback(() => {
        delegates.startFreshSpecificItemPublicationLogSearch({
            type: SearchItemType.KM_POST,
            kmPost,
        });
        navigate('publication-search');
    }, [kmPost, kmPostChangeTime, kmPostCreatedAndChangedTime]);

    const kmPostLengthText =
        infoboxExtras?.kmLength === undefined
            ? t('tool-panel.km-post.layout.no-kilometer-length')
            : infoboxExtras.kmLength < 0
              ? t('tool-panel.km-post.layout.negative-kilometer-length')
              : `${roundToPrecision(infoboxExtras.kmLength, 3)} m`;

    return (
        <React.Fragment>
            <Infobox
                title={t('tool-panel.km-post.layout.general-title')}
                qa-id="km-post-infobox"
                contentVisible={visibilities.basic}
                onContentVisibilityChange={() => visibilityChange('basic')}
                onEdit={openEditDialog}
                iconDisabled={layoutContext.publicationState === 'OFFICIAL'}>
                <InfoboxContent>
                    <InfoboxField
                        qaId="km-post-km-number"
                        label={t('tool-panel.km-post.layout.km-post')}
                        value={updatedKmPost?.kmNumber}
                    />
                    <InfoboxField
                        label={t('tool-panel.km-post.layout.track-number')}
                        qaId="km-post-track-number"
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
                            infoboxExtrasLoading === LoaderStatus.Ready ? (
                                kmPostLengthText
                            ) : (
                                <Spinner />
                            )
                        }
                    />
                    <InfoboxButtons>
                        <Button
                            size={ButtonSize.SMALL}
                            title={
                                !kmPost.layoutLocation
                                    ? t('tool-panel.km-post.layout.no-location')
                                    : ''
                            }
                            disabled={!kmPost.layoutLocation}
                            variant={ButtonVariant.SECONDARY}
                            qa-id="zoom-to-km-post"
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
                onContentVisibilityChange={() => visibilityChange('location')}
                onEdit={openEditDialog}
                iconDisabled={layoutContext.publicationState === 'OFFICIAL'}>
                <InfoboxContent>
                    <InfoboxField
                        qaId="km-post-gk-coordinate-system"
                        label={t('tool-panel.km-post.layout.gk-coordinates.coordinate-system')}
                        value={
                            <CoordinateSystemField
                                kmPostCrs={gkCoordinateSystem}
                                planCrs={geometryPlanCrs}
                            />
                        }
                    />
                    <InfoboxField
                        qaId="km-post-gk-coordinates"
                        label={
                            gkCoordinateSystem === undefined
                                ? ''
                                : t('tool-panel.km-post.layout.gk-coordinates.location', {
                                      coordinateSystemName: gkCoordinateSystem?.name ?? '',
                                  })
                        }
                        value={
                            updatedKmPost?.gkLocation
                                ? formatToGkFinString(updatedKmPost.gkLocation.location)
                                : '-'
                        }
                    />
                    <InfoboxField
                        qaId="km-post-gk-coordinates-confirmed"
                        label={t(`tool-panel.km-post.layout.gk-coordinates.confirmed-title`)}
                        value={t(
                            `tool-panel.km-post.layout.gk-coordinates.${
                                updatedKmPost?.gkLocation?.confirmed ? '' : 'not-'
                            }confirmed`,
                        )}
                    />
                    <InfoboxField
                        qaId="km-post-gk-coordinates-source"
                        label={t('tool-panel.km-post.layout.gk-coordinates.source')}
                        value={
                            updatedKmPost?.gkLocation?.source === 'FROM_GEOMETRY' ? (
                                <span>
                                    {t(
                                        'tool-panel.km-post.layout.gk-coordinates.source-from-geometry',
                                    )}{' '}
                                    <AnchorLink
                                        className={styles['km-post-infobox__plan-link']}
                                        onClick={() => {
                                            if (infoboxExtras?.sourceGeometryPlanId) {
                                                delegates.onSelect({
                                                    geometryPlans: [
                                                        infoboxExtras.sourceGeometryPlanId,
                                                    ],
                                                });
                                                delegates.setToolPanelTab({
                                                    id: infoboxExtras.sourceGeometryPlanId,
                                                    type: 'GEOMETRY_PLAN',
                                                });
                                            }
                                        }}>
                                        {geometryPlan?.fileName}
                                    </AnchorLink>
                                </span>
                            ) : updatedKmPost?.gkLocation?.source === 'FROM_LAYOUT' ? (
                                t('tool-panel.km-post.layout.gk-coordinates.source-from-layout')
                            ) : updatedKmPost?.gkLocation?.source === 'MANUAL' ? (
                                t('tool-panel.km-post.layout.gk-coordinates.source-manual')
                            ) : (
                                '-'
                            )
                        }
                    />
                    <InfoboxField
                        qaId="km-post-coordinates"
                        label={t('tool-panel.km-post.layout.coordinates')}
                        value={
                            updatedKmPost?.layoutLocation
                                ? formatToTM35FINString(updatedKmPost.layoutLocation)
                                : '-'
                        }
                    />
                </InfoboxContent>
            </Infobox>
            <AssetValidationInfoboxContainer
                contentVisible={visibilities.validation}
                onContentVisibilityChange={() => visibilityChange('validation')}
                idAndType={{ id: kmPost.id, type: 'KM_POST' }}
                layoutContext={layoutContext}
                changeTime={kmPostChangeTime}
            />
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
                                value={
                                    kmPostCreatedAndChangedTime.changed
                                        ? formatDateShort(kmPostCreatedAndChangedTime.changed)
                                        : t('tool-panel.unmodified-in-geoviite')
                                }
                            />
                            <Button
                                size={ButtonSize.SMALL}
                                variant={ButtonVariant.SECONDARY}
                                title={openPublicationLogButtonTitle}
                                disabled={!hasPublicationLog}
                                onClick={openPublicationLog}>
                                {t('tool-panel.show-in-publication-log')}
                            </Button>
                        </React.Fragment>
                    )}
                </InfoboxContent>
            </Infobox>

            {showEditDialog && (
                <KmPostEditDialogContainer
                    kmPostId={kmPost.id}
                    onClose={closeEditDialog}
                    onSave={handleKmPostSave}
                    geometryKmPostGkLocation={kmPost.gkLocation?.location}
                    editType={'MODIFY'}
                    geometryPlanSrid={geometryPlan?.units?.coordinateSystemSrid}
                />
            )}
        </React.Fragment>
    );
};

export default KmPostInfobox;
