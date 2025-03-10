import { useEffect } from 'react';

export const useScrollListener = (onScroll: (event: WheelEvent) => void) => {
    useEffect(() => {
        const handleWheel = (event: WheelEvent) => {
            onScroll(event);
        };

        window.addEventListener('wheel', handleWheel);

        return () => {
            window.removeEventListener('wheel', handleWheel);
        };
    }, [onScroll]);
};
