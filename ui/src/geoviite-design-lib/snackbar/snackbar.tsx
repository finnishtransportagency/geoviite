import * as React from 'react';
import { toast, ToastOptions } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import i18n from 'i18next';

type ToastId = string | number;

let snacksInQueue: ToastId[] = [];
let blockToasts = false;

type SnackbarButtonOptions = {
    text: string;
    onClick: () => void;
};

type SnackbarOptions = {
    toastId?: ToastId;
    className?: string;
    showCloseButton?: boolean;
};

function addToQueue(toastId: ToastId): (() => void) | undefined {
    const similarExists = snacksInQueue.includes(toastId);

    if (!similarExists) {
        snacksInQueue.push(toastId);
        return () => {
            snacksInQueue = snacksInQueue.filter((id) => id !== toastId);
        };
    }
}

function getToastId(header: string, body?: string): string {
    return `toast-${header}${body ? '-' + body : ''}`;
}

function getToast(
    headerKey: string,
    bodyKey?: string,
    children?: React.ReactNode,
    button?: SnackbarButtonOptions,
) {
    const t = i18n.t;

    const header = t(headerKey);
    const body = bodyKey ? t(bodyKey) : undefined;
    const buttonText = button ? t(button.text) : undefined;

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
                {children}
            </div>
            {button && (
                <div
                    className={styles['Toastify__button']}
                    onClick={button.onClick}
                    title={buttonText}>
                    {buttonText}
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

export function info(
    header: string,
    body?: string,
    opts?: SnackbarOptions,
    children?: React.ReactNode,
) {
    const { toastId: id, showCloseButton, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const removeFunction = addToQueue(toastId);

    const toastOptions: ToastOptions = {
        toastId: toastId,
        onClose: removeFunction,
        icon: <Icons.Info />,
        ...(showCloseButton
            ? {
                  autoClose: false,
                  closeOnClick: false,
                  closeButton: CloseButton,
                  className: styles['Toastify__toast--with-close-button'],
              }
            : {}),
        ...options,
    };

    if (removeFunction && !blockToasts) {
        toast.warn(getToast(header, body, children), toastOptions);
    }
}

export function success(header: string, body?: string, opts?: SnackbarOptions) {
    const { toastId: id, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const removeFunction = addToQueue(toastId);

    if (removeFunction && !blockToasts) {
        const toastOptions = {
            toastId: toastId,
            onClose: removeFunction,
            icon: <Icons.Selected />,
            ...options,
        };

        toast.success(getToast(header, body), toastOptions);
    }
}

export function error(header: string, body?: string, opts?: SnackbarOptions) {
    const { toastId: id, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const removeFunction = addToQueue(toastId);

    if (removeFunction && !blockToasts) {
        const toastOptions: ToastOptions = {
            toastId: toastId,
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            onClose: removeFunction,
            icon: <Icons.StatusError />,
            ...options,
        };

        toast.error(getToast(header, body), toastOptions);
    }
}

export function sessionExpired() {
    if (!blockToasts) {
        snacksInQueue = [];
        blockToasts = true;

        toast.dismiss();
        toast.clearWaitingQueue();

        const buttonOptions = {
            text: 'unauthorized-request.button',
            onClick: () => location.reload(),
        };

        const toastOptions: ToastOptions = {
            autoClose: false,
            closeButton: false,
            closeOnClick: false,
            icon: <Icons.StatusError />,
        };

        toast.error(
            getToast('unauthorized-request.title', undefined, undefined, buttonOptions),
            toastOptions,
        );
    }
}
