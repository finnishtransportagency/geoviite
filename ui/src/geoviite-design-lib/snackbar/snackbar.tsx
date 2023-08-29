import * as React from 'react';
import { toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import i18n from 'i18next';

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

function getToastBody(
    header: string,
    body?: string,
    qaId?: string,
    button?: SnackbarButtonOptions,
) {
    return (
        <div className={styles['Toastify__toast-content']} qa-id={qaId}>
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

const CloseButton = ({ closeToast }: never) => (
    <button className={styles['Toastify__close-button']} onClick={closeToast}>
        <Icons.Close color={IconColor.INHERIT} />
    </button>
);

type ToastOpts = {
    body?: string;
    qaId?: string;
    className?: string;
};

export function info(header: string, opts?: ToastOpts) {
    const { body, qaId, ...toastOpts } = opts ?? {};
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.warn(getToastBody(header, body, qaId), {
            onClose: removeFunction,
            icon: Icons.Info,
            ...toastOpts,
        });
    }
}

export function success(header: string, opts?: ToastOpts) {
    const { body, qaId, ...toastOpts } = opts ?? {};
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.success(getToastBody(header, body, qaId), {
            onClose: removeFunction,
            icon: Icons.Selected,
            ...toastOpts,
        });
    }
}

export function error(header: string, opts?: ToastOpts) {
    const { body, qaId, ...toastOpts } = opts ?? {};
    const removeFunction = addToQueue(header, body);

    if (removeFunction && !blockToasts) {
        toast.error(getToastBody(header, body, qaId), {
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            onClose: removeFunction,
            icon: Icons.StatusError,
            ...toastOpts,
        });
    }
}

export function sessionExpired() {
    if (!blockToasts) {
        snacksInQueue = [];
        blockToasts = true;

        toast.dismiss();
        toast.clearWaitingQueue();

        toast.error(
            getToastBody(i18n.t('unauthorized-request.title'), undefined, undefined, {
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
