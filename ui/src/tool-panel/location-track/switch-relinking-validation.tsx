import * as React from 'react';
import i18n from 'i18next';

import {
    LayoutLocationTrack,
    LayoutSwitch,
    LocationTrackId,
} from 'track-layout/track-layout-model';
import { validateLocationTrackSwitchRelinking } from 'linking/linking-api';
import { getSwitches } from 'track-layout/layout-switch-api';
import { getChangeTimes } from 'common/change-time-api';
import { Spinner } from 'vayla-design-lib/spinner/spinner';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import { LoaderStatus, useLoaderWithStatus } from 'utils/react-utils';

type SwitchRelinkingValidationBodyProps = {
    locationTrackId: LocationTrackId;
    onSwitchClick: (layoutSwitch: LayoutSwitch) => void;
};

const SwitchRelinkingValidationBody: React.FC<SwitchRelinkingValidationBodyProps> = ({
    locationTrackId,
    onSwitchClick,
}) => {
    const changeTimes = getChangeTimes();

    const [switchWithErrors, loadingStatus] = useLoaderWithStatus(async () => {
        const switchIds = (await validateLocationTrackSwitchRelinking(locationTrackId))
            .filter((r) => r.validationErrors.length > 0)
            .map((s) => s.id);

        return await getSwitches(switchIds, 'DRAFT');
    }, [changeTimes.layoutSwitch, changeTimes.layoutLocationTrack]);

    return (
        <React.Fragment>
            {loadingStatus == LoaderStatus.Loading ? (
                <Spinner />
            ) : (
                <span className="Toastify__toast-body location-track-infobox__split-validated-switches">
                    {switchWithErrors?.map((r) => (
                        <a key={r.id} onClick={() => onSwitchClick(r)}>
                            {r.name}
                        </a>
                    ))}
                </span>
            )}
        </React.Fragment>
    );
};

export function showTrackSwitchRelinkingValidations(
    locationTrack: LayoutLocationTrack,
    onSwitchClick: (layoutSwitch: LayoutSwitch) => void,
) {
    Snackbar.info(
        i18n.t('tool-panel.location-track.switch-relinking.toast-title', {
            locationTrack: locationTrack.name,
        }),
        undefined,
        { showCloseButton: true },
        <SwitchRelinkingValidationBody
            locationTrackId={locationTrack.id}
            onSwitchClick={onSwitchClick}
        />,
    );
}
