import * as React from 'react';
import { Id, toast, ToastOptions } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import { useTranslation } from 'react-i18next';
import { ApiErrorResponse } from 'api/api-fetch';
import { Button, ButtonSize, ButtonVariant } from 'vayla-design-lib/button/button';

let blockToasts = false;

type SnackbarButtonOptions = {
    text: string;
    onClick: () => void;
};

type SnackbarOptions = {
    toastId?: Id;
    className?: string;
    showCloseButton?: boolean;
    replace?: boolean;
};

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

type ApiErrorToastProps = {
    header: string;
    path: string;
    apiError: ApiErrorResponse;
};

const ToastTextContent: React.FC<ToastTextContentProps> = ({ header, body }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['Toastify__toast-text']}>
            <span className={styles['Toastify__toast-header']} title={t(header)}>
                {t(header)}
            </span>
            {body && (
                <p className={styles['Toastify__toast-text-body']} title={t(body)}>
                    {t(body)}
                </p>
            )}
        </div>
    );
};

const ToastButtonContent: React.FC<ToastButtonContentProps> = ({ button }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['Toastify__button']} onClick={button.onClick} title={t(button.text)}>
            {t(button.text)}
        </div>
    );
};

const ToastContent: React.FC<ToastContentProps> = ({ children }) => {
    return <div className={styles['Toastify__toast-content']}>{children}</div>;
};

const ApiErrorToast: React.FC<ApiErrorToastProps> = ({ header, path, apiError }) => {
    const { t, i18n } = useTranslation();

    const date = new Date(apiError.timestamp).toLocaleString(i18n.language);

    const copyToClipboard = () => {
        navigator.clipboard
            .writeText(JSON.stringify({ path: path, error: apiError }))
            .then(() => success(t('error.copied-to-clipboard')))
            .catch(() => error(t('error.copy-to-clipboard-failed')));
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

const ButtonOptions: ToastOptions = {
    autoClose: false,
    closeOnClick: false,
    closeButton: CloseButton,
    className: styles['Toastify__toast--with-close-button'],
};

function getToastId(header: string, body?: string): Id {
    return `toast-${header}${body ? '-' + body : ''}`;
}

function getToastContent(header: string, body?: string, children?: React.ReactNode) {
    return (
        <ToastContent>{children ?? <ToastTextContent header={header} body={body} />}</ToastContent>
    );
}

export function info(
    header: string,
    body?: string,
    opts?: SnackbarOptions,
    children?: React.ReactNode,
): Id | undefined {
    const { toastId: id, showCloseButton, replace, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const exists = toast.isActive(toastId);

    if (!blockToasts) {
        const toastOptions: ToastOptions = {
            toastId: toastId,
            icon: <Icons.Info />,
            ...(showCloseButton ? ButtonOptions : {}),
            ...options,
        };

        const toastContent = getToastContent(header, body, children);

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

export function success(
    header: string,
    body?: string,
    opts?: SnackbarOptions,
    children?: React.ReactNode,
): Id | undefined {
    const { toastId: id, replace, showCloseButton, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const exists = toast.isActive(toastId);

    if (!blockToasts) {
        const toastOptions = {
            toastId: toastId,
            icon: <Icons.Selected />,
            ...(showCloseButton ? ButtonOptions : {}),
            ...options,
        };

        const toastContent = getToastContent(header, body, children);

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

export function error(
    header: string,
    body?: string,
    opts?: SnackbarOptions,
    children?: React.ReactNode,
): Id | undefined {
    const { toastId: id, replace, showCloseButton: _, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const exists = toast.isActive(toastId);

    if (!blockToasts) {
        const toastOptions: ToastOptions = {
            toastId: toastId,
            autoClose: false,
            closeOnClick: false,
            closeButton: CloseButton,
            icon: <Icons.StatusError />,
            ...options,
        };

        const toastContent = getToastContent(header, body, children);

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
    return error(
        '',
        undefined,
        undefined,
        <ApiErrorToast header={header} apiError={apiError} path={path} />,
    );
}
