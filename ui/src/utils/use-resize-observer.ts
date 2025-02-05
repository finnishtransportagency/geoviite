import { useEffect, RefObject } from 'react';

type ResizeDimensions = {
    width: number;
    height: number;
};

type ResizeObserverOptions = {
    ref: RefObject<HTMLElement>;
    onResize: (size: ResizeDimensions) => void;
};

export function useResizeObserver({ ref, onResize }: ResizeObserverOptions) {
    useEffect(() => {
        const observer = new ResizeObserver(([entry]) => {
            if (entry) {
                const dimensions: ResizeDimensions = {
                    width: entry.contentRect.width,
                    height: entry.contentRect.height,
                };
                onResize(dimensions);
            }
        });

        if (ref.current) {
            observer.observe(ref.current);
        }

        return () => observer.disconnect();
    }, [ref, onResize]);
}
