import * as React from 'react';
import { Id, toast, ToastOptions } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import { useTranslation } from 'react-i18next';
import { ApiErrorResponse } from 'api/api-fetch';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';
import { Override } from 'utils/type-utils';

let blockToasts = false;

type SnackbarButtonOptions = {
    text: string;
    onClick: () => void;
};

type SnackbarOptions = {
    id?: Id;
    className?: string;
    replace?: boolean;
};

type SnackbarToastOptions = Override<SnackbarOptions, { id: Id }>;

type ToastContentProps = {
    children: React.ReactNode;
};

type ToastTextContentProps = {
    header: string;
    body?: string;
};

type ToastButtonContentProps = {
    button: SnackbarButtonOptions;
};

type ApiErrorSnackbarProps = {
    header: string;
    path: string;
    apiError: ApiErrorResponse;
};

const ToastTextContent: React.FC<ToastTextContentProps> = ({ header, body }) => {
    const { t } = useTranslation();
    const translatedHeader = t(header);
    const translatedBody = body ? t(body) : undefined;

    return (
        <div className={styles['Toastify__toast-text']}>
            <span className={styles['Toastify__toast-header']} title={translatedHeader}>
                {translatedHeader}
            </span>
            {translatedBody && (
                <p className={styles['Toastify__toast-text-body']} title={translatedBody}>
                    {translatedBody}
                </p>
            )}
        </div>
    );
};

const ToastButtonContent: React.FC<ToastButtonContentProps> = ({ button }) => {
    const { t } = useTranslation();
    const buttonText = t(button.text);

    return (
        <div className={styles['Toastify__button']} onClick={button.onClick} title={buttonText}>
            {buttonText}
        </div>
    );
};

const ToastContent: React.FC<ToastContentProps> = ({ children }) => (
    <div className={styles['Toastify__toast-content']}>{children}</div>
);

const ApiErrorToast: React.FC<ApiErrorSnackbarProps> = ({ header, path, apiError }) => {
    const { t, i18n } = useTranslation();

    const date = new Date(apiError.timestamp).toLocaleString(i18n.language);

    const copyToClipboard = () => {
        navigator.clipboard
            .writeText(JSON.stringify({ path: path, error: apiError }))
            .then(() => success(t('error.copied-to-clipboard')))
            .catch(() => info(t('error.copy-to-clipboard-failed')));
    };

    return (
        <ToastContent>
            <div className={styles['Toastify__toast-text']}>
                <span className={styles['Toastify__toast-header-container']}>
                    <span className={styles['Toastify__toast-header']} title={header}>
                        {header}
                    </span>
                    <Button
                        title={t('error.copy-details-to-clipboard')}
                        onClick={copyToClipboard}
                        variant={ButtonVariant.GHOST}
                        size={ButtonSize.SMALL}
                        icon={Icons.Copy}
                    />
                </span>
                <span className={styles['Toastify__toast-footer']}>
                    {date} | {apiError.correlationId}
                </span>
            </div>
        </ToastContent>
    );
};

const CloseButton = ({ closeToast }: never) => (
    <button className={styles['Toastify__close-button']} onClick={closeToast}>
        <Icons.Close color={IconColor.INHERIT} />
    </button>
);

function getToastId(header: string, body?: string): Id {
    return `toast-${header}${body ? '-' + body : ''}`;
}

export function info(header: string, body?: string, opts?: SnackbarOptions): Id | undefined {
    const toastOptions = { ...opts, id: opts?.id ?? getToastId(header, body) };

    return showInfoToast(
        <ToastContent>
            <ToastTextContent header={header} body={body} />
        </ToastContent>,
        toastOptions,
    );
}

function showInfoToast(toastContent: React.ReactNode, opts: SnackbarToastOptions) {
    const { id: toastId, replace, ...options } = opts;

    if (!blockToasts) {
        const exists = toast.isActive(toastId);

        const toastOptions: ToastOptions = {
            toastId: toastId,
            icon: <Icons.Info />,
            ...options,
        };

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });

            return toastId;
        } else if (!exists) {
            return toast.warn(toastContent, toastOptions);
        }
    }
}

export function success(header: string, body?: string, opts?: SnackbarOptions): Id | undefined {
    const toastOptions: SnackbarToastOptions = {
        ...opts,
        id: opts?.id ?? getToastId(header, body),
    };

    return showSuccessToast(
        <ToastContent>
            <ToastTextContent header={header} body={body} />
        </ToastContent>,
        toastOptions,
    );
}

function showSuccessToast(toastContent: React.ReactNode, opts: SnackbarToastOptions) {
    const { id: toastId, replace, ...options } = opts;

    if (!blockToasts) {
        const exists = toast.isActive(toastId);

        const toastOptions: ToastOptions = {
            toastId: toastId,
            icon: <Icons.Selected />,
            closeOnClick: true,
            ...options,
        };

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });

            return toastId;
        } else if (!exists) {
            return toast.success(toastContent, toastOptions);
        }
    }
}

export function error(header: string, body?: string, opts?: SnackbarOptions): Id | undefined {
    const toastOptions = { ...opts, id: opts?.id ?? getToastId(header, body) };

    return showErrorToast(
        <ToastContent>
            <ToastTextContent header={header} body={body} />
        </ToastContent>,
        toastOptions,
    );
}

function showErrorToast(toastContent: React.ReactNode, opts: SnackbarToastOptions) {
    const { id: toastId, replace, ...options } = opts;

    if (!blockToasts) {
        const exists = toast.isActive(toastId);

        const toastOptions: ToastOptions = {
            toastId: toastId,
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            icon: <Icons.StatusError />,
            ...options,
        };

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });

            return toastId;
        } else if (!exists) {
            return toast.error(toastContent, toastOptions);
        }
    }
}

export function sessionExpired() {
    if (!blockToasts) {
        blockToasts = true;

        toast.clearWaitingQueue();
        toast.dismiss();

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
            <ToastContent>
                <ToastTextContent header="unauthorized-request.title" />
                <ToastButtonContent button={buttonOptions} />
            </ToastContent>,
            toastOptions,
        );
    }
}

export function apiError(header: string, path: string, apiError: ApiErrorResponse): Id | undefined {
    return showErrorToast(
        <ToastContent>
            <ApiErrorToast header={header} apiError={apiError} path={path} />
        </ToastContent>,
        {
            id: getToastId(header, path),
        },
    );
}
