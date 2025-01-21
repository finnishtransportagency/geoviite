import React from 'react';
import { createPortal } from 'react-dom';
import styles from './dialog.scss';
import { createClassName } from 'vayla-design-lib/utils';
import { IconColor, Icons } from 'vayla-design-lib/icon/Icon';

export enum DialogVariant {
    DARK = 'dialog__popup--dark',
    LIGHT = 'dialog__popup--light',
}

export enum DialogWidth {
    NORMAL = 'dialog__popup--normal',
    TWO_COLUMNS = 'dialog__popup--two-columns',
    THREE_COLUMNS = 'dialog__popup--three-columns',
}

export enum DialogHeight {
    RESTRICTED_TO_HALF_OF_VIEWPORT = 'dialog__popup--height-restricted-to-half',
}

export type DialogProps = {
    title?: string;
    children?: React.ReactNode;
    footerContent?: React.ReactNode;
    footerClassName?: string;
    onClose?: () => void;
    variant?: DialogVariant;
    width?: DialogWidth;
    height?: DialogHeight;
    allowClose?: boolean;
    className?: string;
    qaId?: string;
} & Pick<React.HTMLProps<HTMLElement>, 'style'>;

type DragParams = {
    dragPointX: number;
    dragPointY: number;
};

export const Dialog: React.FC<DialogProps> = ({
    allowClose = true,
    width = DialogWidth.NORMAL,
    qaId = undefined,
    ...props
}: DialogProps) => {
    const dialogHeaderRef = React.useRef<HTMLDivElement>(null);
    const [dialogDragParams, setDialogDragParams] = React.useState<DragParams>();
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
    }, [dialogHeaderRef.current]);

    function close() {
        props.onClose && props.onClose();
    }

    return createPortal(
        <div
            className={styles['dialog']}
            onMouseUp={() => setDialogDragParams(undefined)}
            onMouseMove={moveDialog}>
            <div
                className={createClassName(
                    styles['dialog__popup'],
                    styles[width],
                    props.height && styles[props.height],
                    props.className,
                    props.variant && styles[props.variant],
                    dialogDragParams && styles['dialog__popup--moving'],
                )}
                style={{ left: dialogPositionX, top: dialogPositionY }}
                ref={dialogHeaderRef}
                {...(qaId && { 'qa-id': qaId })}>
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
                        <div className={styles['dialog__close']} onClick={close}>
                            <Icons.Close color={IconColor.INHERIT} />
                        </div>
                    )}
                </div>
                <div className={styles['dialog__content']}>{props.children}</div>
                {props.footerContent && (
                    <div
                        className={createClassName(
                            styles['dialog__footer'],
                            props.footerClassName,
                        )}>
                        {props.footerContent}
                    </div>
                )}
            </div>
        </div>,
        document.body,
    );
};

type DialogContentSpreadProps = {
    children: React.ReactNode;
};

export const DialogContentSpread: React.FC<DialogContentSpreadProps> = ({
    ...props
}: DialogContentSpreadProps) => {
    return <div className={styles['dialog__content--spread']}>{props.children}</div>;
};
