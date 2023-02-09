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
    className?: string;
} & Pick<React.HTMLProps<HTMLElement>, 'style'>;

type DragParams = {
    dragPointX: number;
    dragPointY: number;
};
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

    const dialogHeaderRef = React.useRef<HTMLDivElement>(null);
    const [dialogDragParams, setDialogDragParams] = React.useState<DragParams | null>(null);
    const [moved, setMoved] = React.useState(false);
    const [dialogPositionX, setDialogPositionX] = React.useState(0);
    const [dialogPositionY, setDialogPositionY] = React.useState(0);

    const moveDialog = (e: React.MouseEvent) => {
        if (dialogDragParams) {
            setDialogPositionX(e.clientX - dialogDragParams.dragPointX);
            setDialogPositionY(e.clientY - dialogDragParams.dragPointY);
        }
    };

    React.useLayoutEffect(() => {
        const dialogBounds =
            dialogHeaderRef.current && dialogHeaderRef.current.getBoundingClientRect();
        if (dialogBounds && !moved) {
            setDialogPositionX((window.innerWidth - dialogBounds.width) / 2);
            setDialogPositionY((window.innerHeight - dialogBounds.height) / 2);
        }
    });

    function close() {
        props.onClose && props.onClose();
    }

    return (
        <div
            className={className}
            onMouseUp={() => setDialogDragParams(null)}
            onMouseMove={(e) => moveDialog(e)}>
            <div
                className={createClassName(
                    styles['dialog__popup'],
                    props.className,
                    dialogDragParams && styles['dialog__popup--moving'],
                )}
                style={{ left: dialogPositionX, top: dialogPositionY }}
                ref={dialogHeaderRef}>
                <div
                    className={styles['dialog__header']}
                    onMouseDown={(e) => {
                        if (e.button === 0) {
                            setDialogDragParams({
                                dragPointX: e.clientX - dialogPositionX,
                                dragPointY: e.clientY - dialogPositionY,
                            });
                            setMoved(true);
                        }
                    }}>
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
