import * as React from 'react';
import { createPortal } from 'react-dom';
import useResizeObserver from 'use-resize-observer';

export type OpenTowards = 'LEFT' | 'RIGHT';

type CloseableModalProps = {
    positionRef: React.MutableRefObject<HTMLElement | null>;
    openingRef?: React.RefObject<HTMLElement | SVGSVGElement | null>;
    onClickOutside: () => void;
    offsetX?: number;
    offsetY?: number;
    children: React.ReactNode;
    className?: string;
    useRefWidth?: boolean;
    refWidthOffset?: number;
    maxHeight?: number;
    openTowards?: OpenTowards;
};

const WINDOW_MARGIN = 6;

type ModalPosition = {
    top: number;
    left: number;
};

type ModalSize = {
    width?: number;
    maxHeight?: number;
};

export const CloseableModal: React.FC<CloseableModalProps> = ({
    positionRef,
    openingRef,
    onClickOutside,
    children,
    className,
    maxHeight,
    offsetX = 0,
    offsetY = 0,
    refWidthOffset = 0,
    useRefWidth = false,
    openTowards = 'RIGHT',
}: CloseableModalProps) => {
    const [modalPosition, setModalPosition] = React.useState<ModalPosition>();
    const [modalSize, setModalSize] = React.useState<ModalSize>();
    const [refPosition, setRefPosition] = React.useState<ModalPosition>();
    const modalRef = React.useRef<HTMLDivElement>(null);

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (
                positionRef.current &&
                !positionRef.current.contains(event.target as HTMLElement) &&
                (!openingRef?.current || !openingRef.current?.contains(event.target as HTMLElement))
            ) {
                onClickOutside();
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [positionRef]);

    React.useEffect(() => {
        if (refPosition && modalRef.current) {
            const windowHeight = window.innerHeight;
            const windowWidth = window.innerWidth;

            const parentWidth = positionRef.current?.getBoundingClientRect().width ?? 0;
            const modalWidth = modalRef.current.getBoundingClientRect().width ?? 0;
            const x =
                openTowards === 'LEFT'
                    ? refPosition.left + parentWidth + offsetX - modalWidth
                    : refPosition.left + offsetX;
            const y = refPosition.top + offsetY;

            const calculatedMaxHeight = windowHeight - y - WINDOW_MARGIN;
            const widthOverflow = x + modalWidth + WINDOW_MARGIN - windowWidth;

            const newPosition: ModalPosition = { left: x, top: y };

            setModalSize({
                width: modalWidth,
                maxHeight:
                    maxHeight === undefined || maxHeight <= calculatedMaxHeight
                        ? maxHeight
                        : calculatedMaxHeight,
            });

            if (modalWidth < windowWidth) {
                if (widthOverflow > 0) {
                    newPosition.left -= widthOverflow;
                } else if (x < 0) {
                    newPosition.left -= x - WINDOW_MARGIN;
                }
            }

            setModalPosition(newPosition);
        }
    }, [refPosition?.top, refPosition?.left, modalRef]);

    useResizeObserver({
        ref: document.body,
        onResize: () => {
            const ref = positionRef.current;
            if (ref) {
                const { x, y, width } = ref.getBoundingClientRect();
                setRefPosition({ left: x, top: y });

                if (useRefWidth) {
                    setModalSize({ ...modalSize, width: width + refWidthOffset });
                }
            }
        },
    });

    return createPortal(
        <div
            ref={modalRef}
            style={{
                position: 'absolute',
                visibility: modalPosition === undefined ? 'hidden' : undefined,
                ...modalPosition,
                ...modalSize,
            }}
            className={className}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
