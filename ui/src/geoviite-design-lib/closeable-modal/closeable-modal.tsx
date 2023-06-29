import * as React from 'react';
import { createPortal } from 'react-dom';
import useResizeObserver from 'use-resize-observer';

type CloseableModalProps = {
    buttonRef: React.MutableRefObject<HTMLDivElement | null>;
    onClickOutside: (value: boolean) => void;
    offsetX: number;
    offsetY: number;
    children: React.ReactNode;
};

export const CloseableModal: React.FC<CloseableModalProps> = ({
    buttonRef,
    onClickOutside,
    offsetX,
    offsetY,
    children,
}: CloseableModalProps) => {
    const [x, setX] = React.useState<number>();
    const [y, setY] = React.useState<number>();

    React.useEffect(() => {
        function clickHandler(event: MouseEvent) {
            if (buttonRef.current && !buttonRef.current.contains(event.target as HTMLElement)) {
                onClickOutside(false);
            }
            console.log('klikkaus');
        }
        console.log('hei vaan');

        document.addEventListener('click', clickHandler);

        return () => {
            document.removeEventListener('click', clickHandler);
        };
    }, [buttonRef]);

    React.useEffect(() => {
        setX(buttonRef.current?.getBoundingClientRect().x);
    }, [buttonRef.current?.getBoundingClientRect().x]);

    React.useEffect(() => {
        setY(buttonRef.current?.getBoundingClientRect().y);
    }, [buttonRef.current?.getBoundingClientRect().y]);

    useResizeObserver({
        ref: document.body,
        onResize: () => {
            const { x: newX, y: newY } = buttonRef.current?.getBoundingClientRect() ?? {};
            setX(newX);
            setY(newY);
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
