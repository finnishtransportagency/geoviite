import { LayoutTrackNumberId } from 'track-layout/track-layout-model';
import { expectDefined } from 'utils/type-utils';

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

type IndexedColor = { key: TrackNumberColorKey; color: TrackNumberColor };

const colorIndexForId = (colors: IndexedColor[], id: LayoutTrackNumberId) =>
    parseInt(id.replace(/^\D+/g, '')) % colors.length;

export const getColor = (key: TrackNumberColorKey): TrackNumberColor | undefined => {
    return TrackNumberColor[key];
};

export const getColors = (): IndexedColor[] =>
    Object.entries(TrackNumberColor)
        .map(([key, color]) => ({
            key: key as TrackNumberColorKey,
            color,
        }))
        .filter(({ color }) => color !== TrackNumberColor.TRANSPARENT);

export const getDefaultColorKey = (id: LayoutTrackNumberId): TrackNumberColorKey => {
    const colors = getColors();
    return expectDefined(colors[colorIndexForId(colors, id)]).key;
};
