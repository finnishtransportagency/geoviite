import * as React from 'react';
import { createPortal } from 'react-dom';
import { Dimensions, Point } from 'model/geometry';
import useResizeObserver from 'use-resize-observer';

const DEFAULT_GAP_BETWEEN_ANCHOR_ELEMENT_AND_MODAL = 6;

export type ModalPosition = 'LEFT' | 'RIGHT' | 'ABOVE' | 'BELOW';

export type OpenTowards = 'LEFT' | 'RIGHT';

type CloseableModalProps = {
    anchorElementRef: React.MutableRefObject<HTMLElement | null>;
    openingElementRef?: React.RefObject<HTMLElement | SVGSVGElement | null>;
    onClickOutside: () => void;
    children: React.ReactNode;
    className?: string;
    useAnchorElementWidth?: boolean;
    modalPosition?: ModalPosition;
    openTowards?: OpenTowards;
    allowReposition?: boolean;
    margin?: number;
};

type Rect = {
    position: Point;
    size: Dimensions;
};

type HorizontalAnchor = 'LEFT' | 'RIGHT';

type VerticalAnchor = 'TOP' | 'BOTTOM';

type Anchor = {
    horizontal: HorizontalAnchor;
    vertical: VerticalAnchor;
};

type OverflowSolvingMethod = 'NONE' | 'REPOSITION';

function oppositeHorizontalAnchor(anchor: HorizontalAnchor): HorizontalAnchor {
    return anchor == 'LEFT' ? 'RIGHT' : 'LEFT';
}

function oppositeVerticalAnchor(anchor: VerticalAnchor): VerticalAnchor {
    return anchor == 'TOP' ? 'BOTTOM' : 'TOP';
}

function getRectByDOMRect(domRect: DOMRect): Rect {
    return {
        position: {
            x: domRect.x,
            y: domRect.y,
        },
        size: {
            width: domRect.width,
            height: domRect.height,
        },
    };
}

function getProportions(anchor: Anchor): Point {
    return {
        x: anchor.horizontal === 'LEFT' ? 0 : 1,
        y: anchor.vertical === 'TOP' ? 0 : 1,
    };
}

function calculateAnchorPosition(rect: Rect, anchor: Anchor): Point {
    const proportions = getProportions(anchor);
    return {
        x: rect.position.x + rect.size.width * proportions.x,
        y: rect.position.y + rect.size.height * proportions.y,
    };
}

function calculateModalPosition(
    anchorElementRect: Rect,
    anchorElementAnchor: Anchor,
    modalSize: Dimensions,
    modalAnchor: Anchor,
    margin: number,
): Point {
    const anchorElementAnchorPosition = calculateAnchorPosition(
        anchorElementRect,
        anchorElementAnchor,
    );
    const spanDirection = {
        x: modalAnchor.horizontal === 'LEFT' ? 1 : -1,
        y: modalAnchor.vertical === 'TOP' ? 1 : -1,
    };
    const spanPerAxis = {
        x: anchorElementAnchor.horizontal == modalAnchor.horizontal ? 0 : spanDirection.x * margin,
        y: anchorElementAnchor.vertical == modalAnchor.vertical ? 0 : spanDirection.y * margin,
    };
    const modalPositionOffset = calculateAnchorPosition(
        {
            position: { x: 0, y: 0 },
            size: modalSize,
        },
        modalAnchor,
    );
    return {
        x: anchorElementAnchorPosition.x - modalPositionOffset.x + spanPerAxis.x,
        y: anchorElementAnchorPosition.y - modalPositionOffset.y + spanPerAxis.y,
    };
}

function calculateOverflow(viewportSize: Dimensions, rect: Rect): Dimensions {
    const zeroSideCrossing = {
        width: Math.max(0, -rect.position.x),
        height: Math.max(0, -rect.position.y),
    };
    const otherSideCrossing = {
        width: Math.max(0, rect.position.x + rect.size.width - viewportSize.width),
        height: Math.max(0, rect.position.y + rect.size.height - viewportSize.height),
    };
    return {
        width: Math.max(zeroSideCrossing.width, otherSideCrossing.width),
        height: Math.max(zeroSideCrossing.height, otherSideCrossing.height),
    };
}

function getOppositeAnchorByOverflow(anchor: Anchor, overflow: Dimensions) {
    return {
        horizontal:
            overflow.width > 0 ? oppositeHorizontalAnchor(anchor.horizontal) : anchor.horizontal,
        vertical: overflow.height > 0 ? oppositeVerticalAnchor(anchor.vertical) : anchor.vertical,
    };
}

function calculateModalRectAndOverflow(
    viewportSize: Dimensions,
    anchorElementRect: Rect,
    anchorElementAnchor: Anchor,
    modalSize: Dimensions,
    modalAnchor: Anchor,
    margin: number,
): { rect: Rect; overflow: Dimensions } {
    const modalPosition = calculateModalPosition(
        anchorElementRect,
        anchorElementAnchor,
        modalSize,
        modalAnchor,
        margin,
    );

    const modalRect = {
        position: modalPosition,
        size: modalSize,
    };

    const overflow = calculateOverflow(viewportSize, modalRect);

    return {
        rect: modalRect,
        overflow,
    };
}

function findOptimalModalPosition(
    viewportSize: Dimensions,
    anchorElementRect: Rect,
    anchorElementAnchor: Anchor,
    modalSize: Dimensions,
    modalAnchor: Anchor,
    margin: number,
    overflowSolvingMethod: OverflowSolvingMethod,
) {
    const { rect: defaultRect, overflow: defaultOverflow } = calculateModalRectAndOverflow(
        viewportSize,
        anchorElementRect,
        anchorElementAnchor,
        modalSize,
        modalAnchor,
        margin,
    );

    if (
        overflowSolvingMethod === 'REPOSITION' &&
        (defaultOverflow.width > 0 || defaultOverflow.height > 0)
    ) {
        const { rect: repositionedRect, overflow: repositionedOverflow } =
            calculateModalRectAndOverflow(
                viewportSize,
                anchorElementRect,
                getOppositeAnchorByOverflow(anchorElementAnchor, defaultOverflow),
                modalSize,
                getOppositeAnchorByOverflow(modalAnchor, defaultOverflow),
                margin,
            );

        if (
            repositionedOverflow.width < defaultOverflow.width ||
            repositionedOverflow.height < defaultOverflow.height
        ) {
            return repositionedRect;
        } else {
            return defaultRect;
        }
    }

    return defaultRect;
}

function getDefaultAnchors(
    modalPosition: ModalPosition,
    openTowards: OpenTowards,
): [Anchor, Anchor] {
    const horizontalEdgeToAlign = openTowards === 'RIGHT' ? 'LEFT' : 'RIGHT';
    if (modalPosition === 'BELOW') {
        return [
            {
                horizontal: horizontalEdgeToAlign,
                vertical: 'BOTTOM',
            },
            {
                horizontal: horizontalEdgeToAlign,
                vertical: 'TOP',
            },
        ];
    } else if (modalPosition === 'ABOVE') {
        return [
            {
                horizontal: horizontalEdgeToAlign,
                vertical: 'TOP',
            },
            {
                horizontal: horizontalEdgeToAlign,
                vertical: 'BOTTOM',
            },
        ];
    } else if (modalPosition === 'LEFT') {
        return [
            {
                horizontal: 'LEFT',
                vertical: 'TOP',
            },
            {
                horizontal: 'RIGHT',
                vertical: 'TOP',
            },
        ];
    } else if (modalPosition === 'RIGHT') {
        return [
            {
                horizontal: 'RIGHT',
                vertical: 'TOP',
            },
            {
                horizontal: 'LEFT',
                vertical: 'TOP',
            },
        ];
    } else throw new Error('Unhandled modal position');
}

export const CloseableModal: React.FC<CloseableModalProps> = ({
    anchorElementRef,
    openingElementRef,
    onClickOutside,
    children,
    className,
    useAnchorElementWidth = false,
    modalPosition = 'BELOW',
    openTowards = 'RIGHT',
    allowReposition = true,
    margin = DEFAULT_GAP_BETWEEN_ANCHOR_ELEMENT_AND_MODAL,
}: CloseableModalProps) => {
    const [modalRect, setModalRect] = React.useState<Rect>();
    const [lockedWidth, setLockedWidth] = React.useState<number>();
    const [modalRef, setModalRef] = React.useState<HTMLDivElement | null>();

    const [defaultAnchorElementAnchor, defaultModalAnchor] = getDefaultAnchors(
        modalPosition,
        openTowards,
    );

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (
                anchorElementRef.current &&
                !anchorElementRef.current.contains(event.target as HTMLElement) &&
                (!openingElementRef?.current ||
                    !openingElementRef.current?.contains(event.target as HTMLElement))
            ) {
                onClickOutside();
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [anchorElementRef]);

    function updateModalPosition() {
        const anchorDOMRect = anchorElementRef.current?.getBoundingClientRect();
        const modalDOMRect = modalRef?.getBoundingClientRect();

        if (anchorDOMRect && modalDOMRect) {
            const anchorElementRect = getRectByDOMRect(anchorDOMRect);
            const lockedWidth = useAnchorElementWidth ? anchorElementRect.size.width : undefined;

            const modalSize = {
                width: lockedWidth ? lockedWidth : modalDOMRect.width,
                height: modalDOMRect.height,
            };

            const windowSize = {
                width: window.innerWidth,
                height: window.innerHeight,
            };
            const newModalRect = findOptimalModalPosition(
                windowSize,
                anchorElementRect,
                defaultAnchorElementAnchor,
                modalSize,
                defaultModalAnchor,
                margin,
                allowReposition ? 'REPOSITION' : 'NONE',
            );

            setLockedWidth(lockedWidth);
            setModalRect(newModalRect);
        }
    }

    React.useEffect(updateModalPosition, [anchorElementRef, modalRef, children]);

    useResizeObserver({
        ref: document.body,
        onResize: updateModalPosition,
    });

    return createPortal(
        <div
            ref={setModalRef}
            style={{
                position: 'absolute',
                boxSizing: 'border-box',
                visibility: modalRect === undefined ? 'hidden' : undefined,
                left: modalRect?.position?.x,
                top: modalRect?.position?.y,
                width: lockedWidth !== undefined ? lockedWidth : 'auto',
            }}
            className={className}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
