import dialogStyles from 'geoviite-design-lib/dialog/dialog.scss';
import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Dialog } from 'geoviite-design-lib/dialog/dialog';
import { Button, ButtonVariant } from 'vayla-design-lib/button/button';
import { LocationTrackId } from 'track-layout/track-layout-model';
import { relinkTrackSwitches } from 'linking/linking-api';
import { Spinner, SpinnerSize } from 'vayla-design-lib/spinner/spinner';
import { getSwitchesValidation } from 'track-layout/layout-switch-api';
import * as Snackbar from 'geoviite-design-lib/snackbar/snackbar';
import {
    LocationTrackTaskListType,
    trackLayoutActionCreators as TrackLayoutActions,
    TrackLayoutState,
} from 'track-layout/track-layout-slice';
import { useLocationTrackInfoboxExtras } from 'track-layout/track-layout-react-utils';
import { useCommonDataAppSelector } from 'store/hooks';
import { draftLayoutContext, LayoutContext } from 'common/common-model';
import { createDelegates } from 'store/store-utils';
import { useLoader } from 'utils/react-utils';
import { getRelinkableSwitchesCount } from 'track-layout/layout-location-track-api';

type LocationTrackSwitchRelinkingDialogContainerProps = {
    locationTrackId: LocationTrackId;
    layoutContext: LayoutContext;
    name: string;
    closeDialog: () => void;
};

export const LocationTrackSwitchRelinkingDialogContainer: React.FC<
    LocationTrackSwitchRelinkingDialogContainerProps
> = (props) => {
    const delegates = createDelegates(TrackLayoutActions);
    const relinkableSwitchesCount = useLoader(
        () => getRelinkableSwitchesCount(props.locationTrackId, props.layoutContext),
        [props.locationTrackId, props.layoutContext.publicationState, props.layoutContext.branch],
    );

    return relinkableSwitchesCount === undefined ? (
        <React.Fragment />
    ) : (
        <LocationTrackSwitchRelinkingDialog
            {...props}
            showLocationTrackTaskList={delegates.showLocationTrackTaskList}
            relinkableSwitchesCount={relinkableSwitchesCount}
        />
    );
};

type LocationTrackSwitchRelinkingDialogProps = LocationTrackSwitchRelinkingDialogContainerProps & {
    showLocationTrackTaskList: (state: TrackLayoutState['locationTrackTaskList']) => void;
    relinkableSwitchesCount: number;
};

export const LocationTrackSwitchRelinkingDialog: React.FC<
    LocationTrackSwitchRelinkingDialogProps
> = ({
    locationTrackId,
    layoutContext,
    name,
    showLocationTrackTaskList,
    closeDialog,
    relinkableSwitchesCount,
}) => {
    const { t } = useTranslation();
    const [isRelinking, setIsRelinking] = React.useState(false);
    const changeTimes = useCommonDataAppSelector((state) => state.changeTimes);

    const [extraInfo, _] = useLocationTrackInfoboxExtras(
        locationTrackId,
        layoutContext,
        changeTimes,
    );

    const startRelinking = async () => {
        setIsRelinking(true);
        const relinkingResult = await relinkTrackSwitches(layoutContext.branch, locationTrackId);
        closeDialog();
        const validation = await getSwitchesValidation(
            draftLayoutContext(layoutContext),
            relinkingResult.map((r) => r.id),
        );
        const relinkedCount = validation.length;
        const invalidCount = new Set([
            ...validation.filter((s) => s.errors.length > 0).map((v) => v.id),
            ...relinkingResult
                .filter((r) => r.outcome === 'NOT_AUTOMATICALLY_LINKABLE')
                .map((r) => r.id),
        ]).size;
        Snackbar.success(
            'tool-panel.location-track.switch-relinking-dialog.relinking-finished-title',
            t(
                'tool-panel.location-track.switch-relinking-dialog.relinking-finished-with-' +
                    (invalidCount === 0 ? 'no-errors' : 'errors'),
                {
                    relinkedCount,
                    invalidCount,
                },
            ),
        );
        if (invalidCount > 0) {
            showLocationTrackTaskList({
                locationTrackId,
                type: LocationTrackTaskListType.RELINKING_SWITCH_VALIDATION,
                branch: layoutContext.branch,
            });
        }
    };
    return (
        extraInfo && (
            <Dialog
                title={t('tool-panel.location-track.switch-relinking-dialog.title')}
                onClose={closeDialog}
                footerContent={
                    <div className={dialogStyles['dialog__footer-content--centered']}>
                        <Button
                            onClick={closeDialog}
                            variant={ButtonVariant.SECONDARY}
                            disabled={isRelinking}>
                            {t('button.cancel')}
                        </Button>
                        <Button
                            onClick={startRelinking}
                            variant={ButtonVariant.PRIMARY}
                            disabled={isRelinking}>
                            {isRelinking && (
                                <>
                                    <Spinner inline size={SpinnerSize.SMALL} />{' '}
                                </>
                            )}
                            {t('tool-panel.location-track.switch-relinking-dialog.confirm-button', {
                                linkedSwitchesCount: relinkableSwitchesCount,
                            })}
                        </Button>
                    </div>
                }>
                <p>
                    {t('tool-panel.location-track.switch-relinking-dialog.confirm-relinking', {
                        name,
                        linkedSwitchesCount: relinkableSwitchesCount,
                    })}
                </p>
                <p>
                    {t(
                        'tool-panel.location-track.switch-relinking-dialog.confirm-relinking-clarification',
                    )}
                </p>
            </Dialog>
        )
    );
};
