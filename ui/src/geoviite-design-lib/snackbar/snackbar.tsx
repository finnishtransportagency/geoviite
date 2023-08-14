import * as React from 'react';
import { toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import i18n from 'i18next';
import { LayoutLocationTrack, LocationTrackId } from 'track-layout/track-layout-model';

let id = 0;
let snacksInQueue: { header: string; body?: string; id: number }[] = [];
let blockToasts = false;

type SnackbarButtonOptions = {
    text: string;
    onClick: () => void;
};

function addToQueue(header: string, body?: string) {
    const similarExists = snacksInQueue.some(
        (s) => s.header == header && (!body || s.body == body),
    );

    if (!similarExists) {
        id++;
        snacksInQueue.push({ id, header, body });
        return removeFromQueue(id);
    }
}

function removeFromQueue(id: number) {
    return () => {
        snacksInQueue = snacksInQueue.filter((s) => s.id != id);
    };
}

function getToastBody(header: string, body?: string, button?: SnackbarButtonOptions) {
    return (
        <div className={styles['Toastify__toast-content']}>
            <div className={styles['Toastify__toast-text']}>
                <span className={styles['Toastify__toast-header']} title={header}>
                    {header}
                </span>
                {body && (
                    <p className={styles['Toastify__toast-text-body']} title={body}>
                        {body}
                    </p>
                )}
            </div>
            {button && (
                <div
                    className={styles['Toastify__button']}
                    onClick={button.onClick}
                    title={button.text}>
                    {button.text}
                </div>
            )}
        </div>
    );
}

const locationTracksSwitchChangedToastId = (id: LocationTrackId) => `switch-changed-${id}`;

function getSwitchChangedToastBody(
    locationTrack: LayoutLocationTrack,
    changedSwitchName: string,
    otherSwitchName: string | null,
    setLocationTrackToEdit: (locationTrack: LayoutLocationTrack) => void,
) {
    let translation = '';
    if (changedSwitchName && otherSwitchName) {
        translation = i18n.t('topology-switch-changed.both-ends-connected', {
            locationTrackName: locationTrack.name,
            locationTrackDescription: locationTrack.description,
            changedSwitchName,
            otherSwitchName,
        });
    } else if (changedSwitchName || otherSwitchName) {
        translation = i18n.t('topology-switch-changed.single-end-connected', {
            locationTrackName: locationTrack.name,
            locationTrackDescription: locationTrack.description,
            changedSwitchName,
        });
    }
    return (
        <React.Fragment>
            <div className={styles['Toastify__toast-content']}>
                <div className={styles['Toastify__toast-text']}>
                    <p
                        className={styles['Toastify__toast-text-body']}
                        dangerouslySetInnerHTML={{ __html: translation }}
                    />
                </div>
            </div>
            <div className={styles['Toastify__buttons']}>
                <div
                    className={styles['Toastify__button']}
                    onClick={() => {
                        setLocationTrackToEdit(locationTrack);
                        toast.dismiss(locationTracksSwitchChangedToastId(locationTrack.id));
                    }}
                    title={i18n.t('topology-switch-changed.edit-track')}>
                    {i18n.t('topology-switch-changed.edit-track')}
                </div>
                <div
                    className={styles['Toastify__button']}
                    onClick={() => {
                        toast.dismiss(locationTracksSwitchChangedToastId(locationTrack.id));
                    }}
                    title={i18n.t('topology-switch-changed.close-message')}>
                    {i18n.t('topology-switch-changed.close-message')}
                </div>
            </div>
        </React.Fragment>
    );
}

const CloseButton = ({ closeToast }: never) => (
    <button className={styles['Toastify__close-button']} onClick={closeToast}>
        <Icons.Close color={IconColor.INHERIT} />
    </button>
);

export function info(header: string, body?: string) {
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.warn(getToastBody(header, body), {
            onClose: removeFunction,
            icon: Icons.Info,
        });
    }
}

export function success(header: string, body?: string) {
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.success(getToastBody(header, body), {
            onClose: removeFunction,
            icon: Icons.Selected,
        });
    }
}

export function error(header: string, body?: string) {
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.error(getToastBody(header, body), {
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            onClose: removeFunction,
            icon: Icons.StatusError,
        });
    }
}

export function topologySwitchInfoChanged(
    locationTrack: LayoutLocationTrack,
    changedSwitchName: string,
    otherEndName: string | null,
    setLocationTrackToEdit: (locationTrack: LayoutLocationTrack) => void,
) {
    toast.warn(
        getSwitchChangedToastBody(
            locationTrack,
            changedSwitchName,
            otherEndName,
            setLocationTrackToEdit,
        ),
        {
            autoClose: false,
            closeButton: false,
            closeOnClick: false,
            icon: Icons.Info,
            toastId: locationTracksSwitchChangedToastId(locationTrack.id),
        },
    );
}

export function sessionExpired() {
    if (!blockToasts) {
        snacksInQueue = [];
        blockToasts = true;

        toast.dismiss();
        toast.clearWaitingQueue();

        toast.error(
            getToastBody(i18n.t('unauthorized-request.title'), undefined, {
                text: i18n.t('unauthorized-request.button'),
                onClick: () => location.reload(),
            }),
            {
                autoClose: false,
                closeButton: false,
                closeOnClick: false,
                icon: Icons.StatusError,
            },
        );
    }
}
