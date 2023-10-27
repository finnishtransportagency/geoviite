import React from 'react';
import styles from './switch-suggestion-creator-dialog.scss';
import { Dialog, DialogContentSpread, DialogVariant } from 'geoviite-design-lib/dialog/dialog';
import { useTranslation } from 'react-i18next';
import {
    LocationTrackEndpoint,
    SuggestedSwitch,
    SuggestedSwitchCreateParamsAlignmentMapping,
} from 'linking/linking-model';
import { Dropdown } from 'vayla-design-lib/dropdown/dropdown';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LoaderStatus, useLoader, useLoaderWithStatus } from 'utils/react-utils';
import { getSwitchStructures } from 'common/common-api';
import {
    JointNumber,
    PublishType,
    SwitchAlignmentId,
    SwitchStructureId,
} from 'common/common-model';
import { switchJointNumberToString } from 'utils/enum-localization-utils';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { boundingBoxAroundPoints, expandBoundingBox } from 'model/geometry';
import { createSuggestedSwitch } from 'linking/linking-api';
import { filterNotEmpty } from 'utils/array-utils';
import {
    asTrackLayoutSwitchJointConnection,
    getMatchingLocationTrackIdsForJointNumbers,
} from 'linking/linking-utils';
import { getLocationTracksNear } from 'track-layout/layout-location-track-api';
import { IconColor, Icons, IconSize } from 'vayla-design-lib/icon/Icon';
import { MessageBox } from 'geoviite-design-lib/message-box/message-box';
import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';

export type SwitchSuggestionCreatorProps = {
    locationTrackEndpoint: LocationTrackEndpoint;
    onSuggestedSwitchCreated: (suggestedSwitch: SuggestedSwitch) => void;
    onClose: () => void;
    publishType: PublishType;
    suggestedSwitch?: SuggestedSwitch;
    prefilledSwitchStructureId?: SwitchStructureId;
};

type SwitchAlignmentConfig = {
    switchAlignmentId: SwitchAlignmentId;
    switchAlignmentJoints: JointNumber[];
    ascending: boolean | undefined;
    locationTrackId: LocationTrackId | undefined;
};

function hasEnoughDataToTrySwitchSuggestion(
    switchStructureId: SwitchStructureId | undefined,
    alignmentMappings: SuggestedSwitchCreateParamsAlignmentMapping[],
): switchStructureId is SwitchStructureId {
    return switchStructureId != undefined && alignmentMappings.length > 0;
}

export const SwitchSuggestionCreatorDialog: React.FC<SwitchSuggestionCreatorProps> = ({
    locationTrackEndpoint,
    onSuggestedSwitchCreated,
    onClose,
    publishType,
    suggestedSwitch,
    prefilledSwitchStructureId,
}) => {
    const { t } = useTranslation();
    const [switchStructureId, setSwitchStructureId] = React.useState<SwitchStructureId | undefined>(
        prefilledSwitchStructureId || suggestedSwitch?.switchStructure.id,
    );
    const switchStructures = useLoader(() => getSwitchStructures(), []);
    const nearbyLocationTracks = useLoader(() => {
        const bbox = expandBoundingBox(
            boundingBoxAroundPoints([locationTrackEndpoint.location]),
            100,
        );
        return getLocationTracksNear(publishType, bbox);
    }, []);
    const [switchAlignmentConfigs, setSwitchAlignmentConfigs] = React.useState<
        SwitchAlignmentConfig[]
    >([]);
    const jointConnections = suggestedSwitch?.joints.map((j) =>
        asTrackLayoutSwitchJointConnection(j),
    );

    // When switch structure changes, re-create switch alignment configs
    React.useEffect(() => {
        const switchStructure = switchStructures?.find((s) => s.id == switchStructureId);

        setSwitchAlignmentConfigs(
            switchStructure?.alignments.map((switchAlignment) => {
                const alignmentConfig = switchAlignmentConfigs.find(
                    (config) => config.switchAlignmentId == switchAlignment.id,
                );

                const locationTrackId = alignmentConfig
                    ? alignmentConfig.locationTrackId
                    : jointConnections
                    ? getMatchingLocationTrackIdsForJointNumbers(
                          switchAlignment.jointNumbers,
                          jointConnections,
                      )[0]
                    : undefined;

                const jointPlainNumbers =
                    switchAlignment.jointNumbers.map(switchJointNumberToString);

                return {
                    switchAlignmentId: switchAlignment.id,
                    switchAlignmentJoints: jointPlainNumbers,
                    ascending: alignmentConfig ? alignmentConfig.ascending : true,
                    locationTrackId: locationTrackId,
                };
            }) || [],
        );
    }, [switchStructureId, switchStructures]);

    const [newSuggestedSwitch, newSuggestedSwitchStatus] = useLoaderWithStatus(() => {
        const alignmentMappings = switchAlignmentConfigs
            .map((config) => {
                if (config.locationTrackId) {
                    return {
                        switchAlignmentId: config.switchAlignmentId,
                        locationTrackId: config.locationTrackId,
                        ascending: config.ascending,
                    };
                } else {
                    return undefined;
                }
            })
            .filter(filterNotEmpty);

        if (hasEnoughDataToTrySwitchSuggestion(switchStructureId, alignmentMappings)) {
            return createSuggestedSwitch({
                locationTrackEndpoint: locationTrackEndpoint,
                switchStructureId: switchStructureId,
                alignmentMappings: alignmentMappings,
            });
        }
    }, [locationTrackEndpoint, switchStructureId, switchAlignmentConfigs]);

    const canSelectSuggestedSwitch = !!newSuggestedSwitch;

    function selectSuggestedSwitch() {
        if (newSuggestedSwitch) {
            onSuggestedSwitchCreated(newSuggestedSwitch);
        }
    }

    function selectLocationTrack(
        switchAlignmentId: string,
        locationTrackId: LocationTrackId | undefined,
    ) {
        setSwitchAlignmentConfigs(
            switchAlignmentConfigs.map((config) => {
                if (config.switchAlignmentId == switchAlignmentId) {
                    return {
                        ...config,
                        locationTrackId: locationTrackId,
                    };
                } else {
                    return config;
                }
            }),
        );
    }

    function _toggleSwitchAlignmentDirection(switchAlignmentId: string) {
        setSwitchAlignmentConfigs(
            switchAlignmentConfigs.map((config) => {
                if (config.switchAlignmentId == switchAlignmentId) {
                    return {
                        ...config,
                        switchAlignmentJoints: config.switchAlignmentJoints.reverse(),
                        ascending: !config.ascending,
                    };
                } else {
                    return config;
                }
            }),
        );
    }

    return (
        <Dialog
            title={t('switch-suggestion-creator-dialog.title')}
            onClose={onClose}
            variant={DialogVariant.DARK}
            footerContent={
                <div className={dialogStyles['dialog__footer-content--centered']}>
                    <Button onClick={onClose} variant={ButtonVariant.SECONDARY}>
                        {t('button.cancel')}
                    </Button>
                    <Button disabled={!canSelectSuggestedSwitch} onClick={selectSuggestedSwitch}>
                        {t('switch-suggestion-creator-dialog.start-linking')}
                    </Button>
                </div>
            }>
            <div className={styles['switch-suggestion-creator']}>
                <div className={styles['switch-suggestion-creator__sub-form']}>
                    <div className={styles['switch-suggestion-creator__info-msg']}>
                        {t('switch-suggestion-creator-dialog.info-msg')}
                    </div>
                    <label className={styles['switch-suggestion-creator__sub-form-label']}>
                        {t('switch-suggestion-creator-dialog.switch-type')}
                    </label>
                    <Dropdown
                        value={switchStructureId}
                        options={switchStructures?.map((switchStructure) => ({
                            name: switchStructure.type,
                            value: switchStructure.id,
                        }))}
                        onChange={setSwitchStructureId}
                        searchable
                    />

                    {switchAlignmentConfigs.length > 0 && nearbyLocationTracks && (
                        <React.Fragment>
                            <label>{t('switch-suggestion-creator-dialog.switch-alignments')}</label>
                            <label>
                                {t('switch-suggestion-creator-dialog.location-track-going-through')}
                            </label>
                            {switchAlignmentConfigs &&
                                switchAlignmentConfigs.map((config) => (
                                    <React.Fragment key={config.switchAlignmentId}>
                                        <label
                                            className={
                                                styles[
                                                    'switch-suggestion-creator__switch-alignment-label'
                                                ]
                                            }
                                            onClick={() =>
                                                _toggleSwitchAlignmentDirection(
                                                    config.switchAlignmentId,
                                                )
                                            }>
                                            {config.switchAlignmentJoints.join('-')}
                                            <Icons.SwitchDirection
                                                size={IconSize.SMALL}
                                                color={IconColor.INHERIT}
                                            />
                                        </label>
                                        <Dropdown
                                            canUnselect
                                            value={
                                                nearbyLocationTracks?.find(
                                                    (a) => a.id == config.locationTrackId,
                                                )?.id
                                            }
                                            options={nearbyLocationTracks?.map((track) => ({
                                                name: track.name,
                                                value: track.id,
                                            }))}
                                            onChange={(trackId) =>
                                                selectLocationTrack(
                                                    config.switchAlignmentId,
                                                    trackId,
                                                )
                                            }
                                            searchable
                                        />
                                    </React.Fragment>
                                ))}
                        </React.Fragment>
                    )}
                </div>
            </div>
            <DialogContentSpread>
                <MessageBox
                    pop={newSuggestedSwitchStatus == LoaderStatus.Ready && !newSuggestedSwitch}>
                    {t('switch-suggestion-creator-dialog.failed-to-create-suggestion')}
                </MessageBox>
            </DialogContentSpread>
        </Dialog>
    );
};
