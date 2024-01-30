import * as React from 'react';
import { toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import i18n from 'i18next';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { ApiErrorResponse } from 'api/api-fetch';
import i18next from 'i18next';

type ToastId = string | number;

const COPY_CONFIRM_AUTO_CLOSE_TIMEOUT = 2000;

let snacksInQueue: ToastId[] = [];
let blockToasts = false;

type SnackbarButtonOptions = {
    text: string;
    onClick: () => void;
};

type ToastOpts = {
    toastId?: ToastId;
    className?: string;
    autoClose?: number;
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

const isApiErrorResponse = (
    allegedApiErrorResponse: unknown,
): allegedApiErrorResponse is ApiErrorResponse =>
    typeof allegedApiErrorResponse === 'object' &&
    allegedApiErrorResponse !== null &&
    allegedApiErrorResponse !== undefined &&
    'timestamp' in allegedApiErrorResponse &&
    'correlationId' in allegedApiErrorResponse;

function getToastId(header: string, body?: string): string {
    return `toast-${header}${body ? '-' + body : ''}`;
}

function getToast(headerKey: string, bodyKey?: string, button?: SnackbarButtonOptions) {
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

function getErrorToast(headerKey: string, errorObj?: unknown, button?: SnackbarButtonOptions) {
    const t = i18n.t;

    const validError = isApiErrorResponse(errorObj);

    const header = t(headerKey);
    const buttonText = button ? t(button.text) : undefined;
    const date =
        validError && errorObj
            ? new Date(errorObj.timestamp).toLocaleString(i18next.language)
            : undefined;
    const copyToClipboard = () => {
        if (errorObj) {
            navigator.clipboard
                .writeText(JSON.stringify(errorObj))
                .then(() =>
                    success(t('error.copied-to-clipboard'), undefined, {
                        autoClose: COPY_CONFIRM_AUTO_CLOSE_TIMEOUT,
                    }),
                )
                .catch(() =>
                    error(t('error.copy-to-clipboard-failed'), undefined, {
                        autoClose: COPY_CONFIRM_AUTO_CLOSE_TIMEOUT,
                    }),
                );
        }
    };

    return (
        <div className={styles['Toastify__toast-content']}>
            <div className={styles['Toastify__toast-text']}>
                <span className={styles['Toastify__toast-header-container']}>
                    <span className={styles['Toastify__toast-header']} title={header}>
                        {header}
                    </span>
                    {validError && errorObj && (
                        <Button
                            title={t('error.copy-details-to-clipboard')}
                            onClick={copyToClipboard}
                            variant={ButtonVariant.GHOST}
                            size={ButtonSize.SMALL}
                            icon={Icons.Copy}
                        />
                    )}
                </span>
                {validError && errorObj && (
                    <div
                        className={
                            styles['Toastify__toast-footer']
                        }>{`${date} | ${errorObj.correlationId}`}</div>
                )}
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

export function info(header: string, body?: string, opts?: ToastOpts) {
    const { toastId: id, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const removeFunction = addToQueue(toastId);

    if (removeFunction && !blockToasts) {
        toast.warn(getToast(header, body), {
            toastId: toastId,
            onClose: removeFunction,
            icon: <Icons.Info />,
            ...options,
        });
    }
}

export function success(header: string, body?: string, opts?: ToastOpts) {
    const { toastId: id, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const removeFunction = addToQueue(toastId);

    if (removeFunction && !blockToasts) {
        toast.success(getToast(header, body), {
            toastId: toastId,
            onClose: removeFunction,
            icon: <Icons.Selected />,
            ...options,
        });
    }
}

export function error(header: string, errorResponse?: ApiErrorResponse, options?: ToastOpts) {
    const toastId = getToastId(header);
    const removeFunction = addToQueue(toastId);

    if (removeFunction && !blockToasts) {
        toast.error(getErrorToast(header, errorResponse), {
            toastId: toastId,
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            onClose: removeFunction,
            icon: <Icons.StatusError />,
            ...options,
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
            getToast('unauthorized-request.title', undefined, {
                text: 'unauthorized-request.button',
                onClick: () => location.reload(),
            }),
            {
                autoClose: false,
                closeButton: false,
                closeOnClick: false,
                icon: <Icons.StatusError />,
            },
        );
    }
}
