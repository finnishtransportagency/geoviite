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
    refWidthOffset?: number;
};

const WINDOW_MARGIN = 6;

export const CloseableModal: React.FC<CloseableModalProps> = ({
    positionRef,
    onClickOutside,
    children,
    className,
    offsetX = 0,
    offsetY = 0,
    refWidthOffset = 0,
    useRefWidth = false,
}: CloseableModalProps) => {
    const [x, setX] = React.useState<number>();
    const [y, setY] = React.useState<number>();
    const [width, setWidth] = React.useState<number | undefined>();
    const modalRef = React.useRef<HTMLDivElement>(null);

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

    React.useEffect(() => {
        const ref = modalRef.current;
        if (ref) {
            const {
                x: trueX,
                y: trueY,
                width: trueWidth,
                height: trueHeight,
            } = ref.getBoundingClientRect();

            const maxHeight = window.innerHeight;
            const maxWidth = window.innerWidth;

            const heightOverflow = trueHeight + trueY - maxHeight + WINDOW_MARGIN;
            const widthOverflow = trueWidth + trueX - maxWidth + WINDOW_MARGIN;

            if (heightOverflow > 0 && trueHeight < maxHeight) {
                setY(trueY - offsetY - heightOverflow);
            }

            if (widthOverflow > 0 && trueWidth < maxWidth) {
                setX(trueX - offsetX - widthOverflow);
            }
        }
    }, [x, y, width]);

    useResizeObserver({
        ref: document.body,
        onResize: () => {
            const ref = positionRef.current;
            if (ref) {
                const { x: newX, y: newY, width: newWidth } = ref.getBoundingClientRect();

                setX(newX);
                setY(newY);

                if (useRefWidth) {
                    setWidth(newWidth ? newWidth + refWidthOffset : undefined);
                }
            }
        },
    });

    return createPortal(
        <div
            ref={modalRef}
            style={{
                top: (y ?? 0) + offsetY,
                left: (x ?? 0) + offsetX,
                position: 'absolute',
                width: width,
                visibility: x === undefined || y === undefined ? 'hidden' : undefined,
            }}
            className={className}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
