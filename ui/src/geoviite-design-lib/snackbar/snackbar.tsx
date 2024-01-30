import * as React from 'react';
import { Id, toast, ToastOptions } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.minimal.css';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';
import './snackbar.scss';
import styles from './snackbar.scss';
import { useTranslation } from 'react-i18next';

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

function getToastId(header: string, body?: string): Id {
    return `toast-${header}${body ? '-' + body : ''}`;
}

type ToastContentProps = {
    header: string;
    body?: string;
    children?: React.ReactNode;
    button?: SnackbarButtonOptions;
};

const ToastContent: React.FC<ToastContentProps> = ({ header, body, button, children }) => {
    const { t } = useTranslation();

    return (
        <div className={styles['Toastify__toast-content']}>
            <div className={styles['Toastify__toast-text']}>
                <span className={styles['Toastify__toast-header']} title={t(header)}>
                    {t(header)}
                </span>
                {body && (
                    <p className={styles['Toastify__toast-text-body']} title={t(body)}>
                        {t(body)}
                    </p>
                )}
                {children}
            </div>
            {button && (
                <div
                    className={styles['Toastify__button']}
                    onClick={button.onClick}
                    title={t(button.text)}>
                    {t(button.text)}
                </div>
            )}
        </div>
    );
};

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
    const { toastId: id, showCloseButton, replace, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const exists = toast.isActive(toastId);

    if (!blockToasts) {
        const toastOptions: ToastOptions = {
            toastId: toastId,
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

        const toastContent = (
            <ToastContent header={header} body={body}>
                {children}
            </ToastContent>
        );

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });
        } else if (!exists) {
            toast.warn(toastContent, toastOptions);
        }
    }
}

export function success(header: string, body?: string, opts?: SnackbarOptions) {
    const { toastId: id, replace, ...options } = opts ?? {};

    const toastId = id ?? getToastId(header, body);
    const exists = toast.isActive(toastId);

    if (!blockToasts) {
        const toastOptions = {
            toastId: toastId,
            icon: <Icons.Selected />,
            ...options,
        };

        const toastContent = <ToastContent header={header} body={body} />;

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });
        } else if (!exists) {
            toast.success(toastContent, toastOptions);
        }
    }
}

export function error(header: string, body?: string, opts?: SnackbarOptions) {
    const { toastId: id, replace, ...options } = opts ?? {};

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

        const toastContent = <ToastContent header={header} body={body} />;

        if (exists && replace) {
            toast.update(toastId, {
                render: toastContent,
                ...toastOptions,
            });
        } else if (!exists) {
            toast.error(toastContent, toastOptions);
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
            <ToastContent header="unauthorized-request.title" button={buttonOptions} />,
            toastOptions,
        );
    }
}
