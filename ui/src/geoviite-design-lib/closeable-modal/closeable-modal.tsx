import * as React from 'react';
import { createPortal } from 'react-dom';
import useResizeObserver from 'use-resize-observer';

type CloseableModalProps = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    onClickOutside: () => void;
    offsetX: number;
    offsetY: number;
    children: React.ReactNode;
};

export const CloseableModal: React.FC<CloseableModalProps> = ({
    positionRef,
    onClickOutside,
    offsetX,
    offsetY,
    children,
}: CloseableModalProps) => {
    const [x, setX] = React.useState<number>();
    const [y, setY] = React.useState<number>();

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
            setX(positionRef.current?.getBoundingClientRect().x);
            setY(positionRef.current?.getBoundingClientRect().y);
        },
    });

    if (x === undefined || y === undefined) return <></>;

    return createPortal(
        <div
            style={{
                top: y + offsetY,
                left: x + offsetX,
                position: 'absolute',
            }}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
