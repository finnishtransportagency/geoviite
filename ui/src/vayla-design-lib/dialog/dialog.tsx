import React from 'react';
import styles from './dialog.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { Button } from 'vayla-design-lib/button/button';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';

export enum DialogVariant {
    DARK = 'dialog--dark',
    LIGHT = 'dialog--light',
}

export type DialogProps = {
    title?: string;
    children?: React.ReactNode;
    footerContent?: React.ReactNode;
    footerClassName?: string;
    onClose?: () => void;
    variant?: DialogVariant;
    scrollable?: boolean;
    allowClose?: boolean;
} & Pick<React.HTMLProps<HTMLElement>, 'style'>;

export const Dialog: React.FC<DialogProps> = ({
    scrollable = true,
    allowClose = true,
    ...props
}: DialogProps) => {
    const className = createClassName(
        styles['dialog'],
        props.variant && styles[props.variant],
        scrollable && styles['dialog--scrollable'],
    );

    function close() {
        props.onClose && props.onClose();
    }

    return (
        <div className={className}>
            <div className={styles['dialog__popup']} style={props.style}>
                <div className={styles['dialog__header']}>
                    <span className={styles['dialog__title']}>{props.title}</span>
                    {allowClose && (
                        <div className={styles['dialog__close']} onClick={() => close()}>
                            <Icons.Close color={IconColor.INHERIT} />
                        </div>
                    )}
                </div>
                <div className={styles['dialog__content']}>{props.children}</div>
                <div
                    className={
                        props.footerClassName
                            ? props.footerClassName
                            : styles['dialog-footer--centered']
                    }>
                    {props.footerContent || <Button onClick={() => close()}>OK</Button>}
                </div>
            </div>
        </div>
    );
};

type DialogContentSpreadProps = {
    children: React.ReactNode;
};

export const DialogContentSpread: React.FC<DialogContentSpreadProps> = ({
    ...props
}: DialogContentSpreadProps) => {
    return <div className={styles['dialog__content-spread']}>{props.children}</div>;
};
