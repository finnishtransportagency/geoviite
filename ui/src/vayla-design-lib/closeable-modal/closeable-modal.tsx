import * as React from 'react';
import { createPortal } from 'react-dom';
import { Dimensions, Point } from 'model/geometry';
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

// const WINDOW_MARGIN = 6;

const SPAN_BETWEEN_ANCHOR_AND_MODAL = 6;

// type ModalPosition = {
//     top: number;
//     left: number;
// };
//
// type ModalSize = {
//     width?: number;
//     maxHeight?: number;
// };

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

type OverhangSolveMethod = 'NONE' | 'REPOSITION';

function oppositeHorizontalAnchor(anchor: HorizontalAnchor): HorizontalAnchor {
    return anchor == 'LEFT' ? 'RIGHT' : 'LEFT';
}

function oppositeVerticalAnchor(anchor: VerticalAnchor): VerticalAnchor {
    return anchor == 'TOP' ? 'BOTTOM' : 'TOP';
}

// const emptyRect = {
//     position: { x: 0, y: 0 },
//     size: { width: 0, height: 0 },
// };

function getRect(domRect: DOMRect): Rect {
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
    parentRect: Rect,
    parentAnchor: Anchor,
    modalSize: Dimensions,
    modalAnchor: Anchor,
    span: number = SPAN_BETWEEN_ANCHOR_AND_MODAL,
): Point {
    const parentAnchorPosition = calculateAnchorPosition(parentRect, parentAnchor);
    const spanDirection = {
        x: modalAnchor.horizontal === 'LEFT' ? 1 : -1,
        y: modalAnchor.vertical === 'TOP' ? 1 : -1,
    };
    const spanPerAxis = {
        x: parentAnchor.horizontal == modalAnchor.horizontal ? 0 : spanDirection.x * span,
        y: parentAnchor.vertical == modalAnchor.vertical ? 0 : spanDirection.y * span,
    };
    const modalPositionOffset = calculateAnchorPosition(
        {
            position: { x: 0, y: 0 },
            size: modalSize,
        },
        modalAnchor,
    );
    return {
        x: parentAnchorPosition.x - modalPositionOffset.x + spanPerAxis.x,
        y: parentAnchorPosition.y - modalPositionOffset.y + spanPerAxis.y,
    };
}

function calculateOverhang(viewportSize: Dimensions, rect: Rect): Point {
    const zeroSideCrossing = {
        x: Math.max(0, -rect.position.x),
        y: Math.max(0, -rect.position.y),
    };
    const otherSideCrossing = {
        x: Math.max(0, rect.position.x + rect.size.width - viewportSize.width),
        y: Math.max(0, rect.position.y + rect.size.height - viewportSize.height),
    };
    return {
        x: Math.max(zeroSideCrossing.x, otherSideCrossing.x),
        y: Math.max(zeroSideCrossing.y, otherSideCrossing.y),
    };
}

function getOppositeAnchorByOverhang(anchor: Anchor, overhang: Point) {
    return {
        horizontal:
            overhang.x > 0 ? oppositeHorizontalAnchor(anchor.horizontal) : anchor.horizontal,
        vertical: overhang.y > 0 ? oppositeVerticalAnchor(anchor.vertical) : anchor.vertical,
    };
}

function calculatePositionAndSize(
    viewportSize: Dimensions,
    parentRect: Rect,
    parentAnchor: Anchor,
    modalSize: Dimensions,
    modalAnchor: Anchor,
    overhangSolveMethod: OverhangSolveMethod,
    span: number = SPAN_BETWEEN_ANCHOR_AND_MODAL,
): { position: Point; size: Dimensions } {
    const modalPosition = calculateModalPosition(
        parentRect,
        parentAnchor,
        modalSize,
        modalAnchor,
        span,
    );

    const modelRect = {
        position: modalPosition,
        size: modalSize,
    };

    const overhang = calculateOverhang(viewportSize, modelRect);

    if (overhangSolveMethod === 'REPOSITION' && (overhang.x > 0 || overhang.y > 0)) {
        return calculatePositionAndSize(
            viewportSize,
            parentRect,
            getOppositeAnchorByOverhang(parentAnchor, overhang),
            modalSize,
            getOppositeAnchorByOverhang(modalAnchor, overhang),
            'NONE',
            span,
        );
    } else {
        return modelRect;
    }
}

export const CloseableModal: React.FC<CloseableModalProps> = ({
    positionRef: parentRef,
    openingRef,
    onClickOutside,
    children,
    className,
    // maxHeight,
    // offsetX = 0,
    // offsetY = 0,
    refWidthOffset = 0,
    useRefWidth = false,
    // openTowards = 'RIGHT',
}: CloseableModalProps) => {
    const [, forceUpdate] = React.useReducer((x) => x + 1, 0);
    const [modalRef, setModalRef] = React.useState<HTMLDivElement | null>();

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (
                parentRef.current &&
                !parentRef.current.contains(event.target as HTMLElement) &&
                (!openingRef?.current || !openingRef.current?.contains(event.target as HTMLElement))
            ) {
                onClickOutside();
            }
        }

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [parentRef]);

    const parentDOMRect = parentRef.current?.getBoundingClientRect();
    const parentRect = parentDOMRect && getRect(parentDOMRect);
    const modalDOMRect = modalRef?.getBoundingClientRect();
    const modalSize = parentRect &&
        modalDOMRect && {
            width: useRefWidth ? parentRect.size.width : modalDOMRect.width,
            height: modalDOMRect.height,
        };
    const windowSize = {
        width: window.innerWidth,
        height: window.innerHeight,
    };
    // console.log('rerender');
    // console.log('modalRef.current', modalRef);
    // console.log(parentDOMRect, modalDOMRect);
    const { position, size: modalRefittedSize } =
        parentRect && modalSize
            ? calculatePositionAndSize(
                  windowSize,
                  parentRect,
                  {
                      horizontal: 'LEFT',
                      vertical: 'BOTTOM',
                  },
                  modalSize,
                  {
                      horizontal: 'LEFT',
                      vertical: 'TOP',
                  },
                  'REPOSITION',
              )
            : { position: undefined, size: undefined };

    useResizeObserver({
        ref: document.body,
        onResize: forceUpdate,
    });

    return createPortal(
        <div
            ref={(x) => setModalRef(x)}
            style={{
                position: 'absolute',
                visibility: position === undefined ? 'hidden' : undefined,
                left: position?.x,
                top: position?.y,
                ...modalRefittedSize,
            }}
            className={className}
            onClick={(e) => e.stopPropagation()}>
            {children}
        </div>,
        document.body,
    );
};
