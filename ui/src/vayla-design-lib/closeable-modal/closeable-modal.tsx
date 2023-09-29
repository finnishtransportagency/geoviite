import * as React from 'react';
import { createPortal } from 'react-dom';
import useResizeObserver from 'use-resize-observer';

type CloseableModalProps = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    onClickOutside: () => void;
    offsetX?: number;
    offsetY?: number;
    children: React.ReactNode;
    className?: string;
    useRefWidth?: boolean;
    offsetWidth?: number;
};

export const CloseableModal: React.FC<CloseableModalProps> = ({
    positionRef,
    onClickOutside,
    children,
    className,
    offsetX = 0,
    offsetY = 0,
    offsetWidth = 0,
    useRefWidth = false,
}: CloseableModalProps) => {
    const [x, setX] = React.useState<number>();
    const [y, setY] = React.useState<number>();
    const [width, setWidth] = React.useState<number | undefined>();

    const boundingRect = positionRef.current?.getBoundingClientRect();

    React.useEffect(() => {
        setX(boundingRect?.x);
        setY(boundingRect?.y);
    }, [boundingRect?.x, boundingRect?.y]);

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (positionRef.current && !positionRef.current.contains(event.target as HTMLElement)) {
                onClickOutside();
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [positionRef]);

    useResizeObserver({
        ref: document.body,
        onResize: () => {
            setX(boundingRect?.x);
            setY(boundingRect?.y);

            if (useRefWidth) {
                setWidth(boundingRect?.width ? boundingRect?.width + offsetWidth : undefined);
            }
        },
    });

    if (x === undefined || y === undefined) return <></>;

    return createPortal(
        <div
            style={{
                top: y + offsetY,
                left: x + offsetX,
                position: 'absolute',
                width: width,
            }}
            className={className}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
