import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { getUnsafe } from 'utils/type-utils';

export type TrackNumberColorKey = keyof typeof TrackNumberColor;
export enum TrackNumberColor {
    TRANSPARENT = 'TRANSPARENT',
    GRAY = '#858585',
    FIG = '#00b0cc',
    BLUE = '#0066cc',
    GREEN = '#27b427',
    LEMON = '#ffc300',
    RED = '#de3618',
    PITAYA = '#e50083',
    EGGPLANT = '#a050a0',
}

export const getColor = (key: TrackNumberColorKey): TrackNumberColor | undefined => {
    return TrackNumberColor[key];
};

export const getColors = () => {
    return Object.entries(TrackNumberColor).filter(
        ([_, v]) => v !== TrackNumberColor.TRANSPARENT,
    ) as [TrackNumberColorKey, TrackNumberColor][];
};

export const getDefaultColorKey = (id: LayoutTrackNumberId): TrackNumberColorKey => {
    const colors = getColors();
    return getUnsafe(colors[parseInt(id.replace(/^\D+/g, '')) % colors.length])[0];
};
