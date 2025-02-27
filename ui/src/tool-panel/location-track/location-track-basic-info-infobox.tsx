import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { LoaderStatus, useLoader } from 'utils/react-utils';
import InfoboxContent from 'tool-panel/infobox/infobox-content';
import InfoboxField from 'tool-panel/infobox/infobox-field';
import LocationTrackTypeLabel from 'geoviite-design-lib/alignment/location-track-type-label';
import { TrackNumberLinkContainer } from 'geoviite-design-lib/track-number/track-number-link';
import InfoboxText from 'tool-panel/infobox/infobox-text';
import { LocationTrackInfoboxDuplicateOf } from 'tool-panel/location-track/location-track-infobox-duplicate-of';
import TopologicalConnectivityLabel from 'tool-panel/location-track/topological-connectivity-label';
import InfoboxButtons from 'tool-panel/infobox/infobox-buttons';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import Infobox from 'tool-panel/infobox/infobox';
import {
    LayoutLocationTrack,
    LayoutSwitchIdAndName,
    LayoutTrackNumber,
} from 'track-layout/track-layout-model';
import { ChangeTimes } from 'common/common-slice';
import { LayoutContext, LocationTrackOwnerId } from 'common/common-model';
import {
    LocationTrackInfoboxVisibilities,
    trackLayoutActionCreators as TrackLayoutActions,
} from 'track-layout/track-layout-slice';
import { getLocationTrackOwners } from 'common/common-api';
import { createDelegates } from 'store/store-utils';
import { OnSelectOptions } from 'selection/selection-model';
import { BoundingBox } from 'model/geometry';
import { useCommonDataAppSelector } from 'store/hooks';
import { getLocationTrackDescriptions } from 'track-layout/layout-location-track-api';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { first } from 'utils/array-utils';
import { LocationTrackState } from 'geoviite-design-lib/location-track-state/location-track-state';
import { LocationTrackOid } from 'track-layout/oid';
import { AnchorLink } from 'geoviite-design-lib/link/anchor-link';

type LocationTrackBasicInfoInfoboxContainerProps = {
    locationTrack: LayoutLocationTrack;
    trackNumber?: LayoutTrackNumber;
    layoutContext: LayoutContext;
    visibilities: LocationTrackInfoboxVisibilities;
    visibilityChange: (key: keyof LocationTrackInfoboxVisibilities) => void;
    openEditLocationTrackDialog: () => void;
    editingDisabled: boolean;
    editingDisabledReason: string | undefined;
};

export const LocationTrackBasicInfoInfoboxContainer: React.FC<
    LocationTrackBasicInfoInfoboxContainerProps
> = (props: LocationTrackBasicInfoInfoboxContainerProps) => {
    const delegates = createDelegates(TrackLayoutActions);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);
    return (
        <LocationTrackBasicInfoInfobox
            {...props}
            changeTimes={changeTimes}
            onSelect={delegates.onSelect}
            showArea={delegates.showArea}
        />
    );
};

type LocationTrackBasicInfoInfoboxProps = LocationTrackBasicInfoInfoboxContainerProps & {
    onSelect: (items: OnSelectOptions) => void;
    showArea: (area: BoundingBox) => void;
    changeTimes: ChangeTimes;
};

export const LocationTrackBasicInfoInfobox: React.FC<LocationTrackBasicInfoInfoboxProps> = ({
    locationTrack,
    trackNumber,
    changeTimes,
    layoutContext,
    visibilities,
    visibilityChange,
    openEditLocationTrackDialog,
    editingDisabled,
    editingDisabledReason,
    onSelect,
    showArea,
}) => {
    const { t } = useTranslation();

    const locationTrackOwners = useLoader(() => getLocationTrackOwners(), []);

    function getLocationTrackOwnerName(ownerId: LocationTrackOwnerId) {
        const name = locationTrackOwners?.find((o) => o.id === ownerId)?.name;
        return name ?? '-';
    }

    function getSwitchLink(sw?: LayoutSwitchIdAndName) {
        if (sw) {
            const switchId = sw.id;
            return (
                <AnchorLink
                    onClick={() =>
                        onSelect({
                            switches: [switchId],
                        })
                    }>
                    {sw.name}
                </AnchorLink>
            );
        } else {
            return t('tool-panel.location-track.no-start-or-end-switch');
        }
    }

    const description = useLoader(
        () =>
            getLocationTrackDescriptions([locationTrack.id], layoutContext).then(
                (value) => (value && first(value)?.description) ?? undefined,
            ),
        [
            locationTrack?.id,
            layoutContext.branch,
            layoutContext.publicationState,
            changeTimes.layoutLocationTrack,
        ],
    );
    const [extraInfo, extraInfoLoadingStatus] = useLocationTrackInfoboxExtras(
        locationTrack?.id,
        layoutContext,
        changeTimes,
    );

    return (
        <Infobox
            contentVisible={visibilities.basic && extraInfoLoadingStatus !== LoaderStatus.Loading}
            onContentVisibilityChange={() => visibilityChange('basic')}
            title={t('tool-panel.location-track.basic-info-heading')}
            onEdit={openEditLocationTrackDialog}
            iconDisabled={editingDisabled}
            disabledReason={editingDisabledReason}
            qa-id="location-track-infobox">
            <InfoboxContent>
                <InfoboxField
                    qaId="location-track-oid"
                    label={t('tool-panel.location-track.identifier')}
                    value={
                        <LocationTrackOid
                            id={locationTrack.id}
                            branch={layoutContext.branch}
                            changeTimes={changeTimes}
                            getFallbackTextIfNoOid={() =>
                                t('tool-panel.location-track.unpublished')
                            }
                        />
                    }
                />
                <InfoboxField
                    qaId="location-track-name"
                    label={t('tool-panel.location-track.track-name')}
                    value={locationTrack.name}
                />
                <InfoboxField
                    qaId="location-track-state"
                    label={t('tool-panel.location-track.state')}
                    value={<LocationTrackState state={locationTrack.state} />}
                />
                <InfoboxField
                    qaId="location-track-type"
                    label={t('tool-panel.location-track.type')}
                    value={<LocationTrackTypeLabel type={locationTrack.type} />}
                />
                <InfoboxField
                    qaId="location-track-description"
                    label={t('tool-panel.location-track.description')}
                    value={description}
                />
                <InfoboxField
                    qaId="location-track-track-number"
                    label={t('tool-panel.location-track.track-number')}
                    value={<TrackNumberLinkContainer trackNumberId={trackNumber?.id} />}
                />
                <InfoboxText value={trackNumber?.description} />
                <InfoboxField
                    label={
                        locationTrack.duplicateOf
                            ? t('tool-panel.location-track.duplicate-of')
                            : extraInfo?.duplicates?.length ?? 0 > 0
                            ? t('tool-panel.location-track.has-duplicates')
                            : t('tool-panel.location-track.not-a-duplicate')
                    }
                    value={
                        <LocationTrackInfoboxDuplicateOf
                            targetLocationTrack={locationTrack}
                            existingDuplicate={extraInfo?.duplicateOf}
                            duplicatesOfLocationTrack={extraInfo?.duplicates ?? []}
                            layoutContext={layoutContext}
                            changeTime={changeTimes.layoutLocationTrack}
                            currentTrackNumberId={trackNumber?.id}
                        />
                    }
                />
                <InfoboxField
                    label={t('tool-panel.location-track.topological-connectivity')}
                    value={
                        <TopologicalConnectivityLabel
                            topologicalConnectivity={locationTrack.topologicalConnectivity}
                        />
                    }
                />
                <InfoboxField
                    label={t('tool-panel.location-track.owner')}
                    value={getLocationTrackOwnerName(locationTrack.ownerId)}
                />
                <InfoboxField
                    label={t('tool-panel.location-track.start-switch')}
                    value={extraInfo && getSwitchLink(extraInfo.switchAtStart)}
                />
                <InfoboxField
                    label={t('tool-panel.location-track.end-switch')}
                    value={extraInfo && getSwitchLink(extraInfo.switchAtEnd)}
                />
                <InfoboxButtons>
                    <Button
                        variant={ButtonVariant.SECONDARY}
                        size={ButtonSize.SMALL}
                        qa-id="zoom-to-location-track"
                        title={
                            !locationTrack.boundingBox
                                ? t('tool-panel.location-track.no-geometry')
                                : ''
                        }
                        disabled={!locationTrack.boundingBox}
                        onClick={() =>
                            locationTrack.boundingBox && showArea(locationTrack.boundingBox)
                        }>
                        {t('tool-panel.location-track.show-on-map')}
                    </Button>
                </InfoboxButtons>
            </InfoboxContent>
        </Infobox>
    );
};
