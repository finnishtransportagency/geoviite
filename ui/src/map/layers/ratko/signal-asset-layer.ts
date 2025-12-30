import { Tile } from 'ol/layer';
import { Coordinate } from 'ol/coordinate';
import { State as RenderState } from 'ol/render';
import { MapLayer } from 'map/layers/utils/layer-model';
import { LAYOUT_SRID } from 'track-layout/track-layout-model';
import TileSource from 'ol/source/Tile';
import MVT from 'ol/format/MVT';
import VectorTileLayer from 'ol/layer/VectorTile';
import VectorTileSource from 'ol/source/VectorTile';
import { Style } from 'ol/style';
import { signalSvg } from 'vayla-design-lib/icon/Icon';
import { expectCoordinate } from 'utils/type-utils';
import { filterNotEmpty } from 'utils/array-utils';
import mapStyles from 'map/map.module.scss';
import { drawCircle, wrapCanvasRenderer } from 'map/layers/utils/rendering';
import { API_URI } from 'api/api-fetch';

type RatkoMapAssetCluster = {
    num_geoms: number;
    string_value: string;
};

const ratkoTileLayerExtent = [-548576, 6291456, 1548576, 8388608];
const signalAssetMinZoomInRatkoExtent = 14;
const iconRenderSize = 24;
const clusterBadgeOffset = (iconRenderSize / 2) * 0.85;
const clusterBadgeSize = 8;
const textIconPadding = 4;
const assetFontSize = 11;
const maxNamesPerCluster = 3;

const signalImg: HTMLImageElement = new Image();
signalImg.src = `data:image/svg+xml;utf8,${encodeURIComponent(signalSvg)}`;

const signalImageRenderFunction = wrapCanvasRenderer(
    (coord: Coordinate, { context: ctx, pixelRatio }: RenderState) => {
        const [x, y] = expectCoordinate(coord);
        ctx.drawImage(
            signalImg,
            x - (iconRenderSize / 2) * pixelRatio,
            y - (iconRenderSize / 2) * pixelRatio,
            iconRenderSize * pixelRatio,
            iconRenderSize * pixelRatio,
        );
    },
);

function createSignalStyle(numGeoms: number, names: string[]) {
    return new Style({
        renderer: wrapCanvasRenderer(
            (coord: Coordinate, { context: ctx, pixelRatio }: RenderState) => {
                ctx.font = `${pixelRatio * assetFontSize}px sans-serif`;
                ctx.lineWidth = pixelRatio;

                ctx.lineWidth = pixelRatio;
                ctx.textAlign = 'left';
                ctx.textBaseline = 'middle';
                const [x, y] = expectCoordinate(coord);

                if (names.length <= maxNamesPerCluster) {
                    const lineHeight = assetFontSize * pixelRatio * 1.2;
                    const textX = x + (iconRenderSize / 2 + textIconPadding) * pixelRatio;
                    const textY = y + pixelRatio - ((names.length - 1) * lineHeight) / 2.0;
                    const paddingHor = 2;
                    const paddingVer = 1;

                    const maxTextWidth = Math.max(
                        ...names.map((name) => ctx.measureText(name).width),
                    );
                    const backgroundX = textX - paddingHor * pixelRatio - pixelRatio;
                    const backgroundY =
                        textY - (assetFontSize * pixelRatio) / 2 - paddingVer * pixelRatio;
                    const backgroundWidth = maxTextWidth + paddingHor * 2 * pixelRatio;
                    const backgroundHeight =
                        lineHeight * names.length +
                        paddingVer * 2 * pixelRatio -
                        (lineHeight - assetFontSize * pixelRatio);

                    ctx.beginPath();
                    ctx.fillStyle = mapStyles.assetNameBackground;
                    ctx.rect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
                    ctx.fill();

                    ctx.fillStyle = mapStyles.assetNameColor;
                    names.forEach((name, i) => {
                        ctx.fillText(name, textX, textY + i * lineHeight);
                    });
                }

                if (numGeoms !== names.length || numGeoms > maxNamesPerCluster) {
                    ctx.beginPath();
                    ctx.fillStyle = mapStyles.assetClusterBadgeBackground;
                    drawCircle(
                        ctx,
                        x + clusterBadgeOffset,
                        y - clusterBadgeOffset,
                        clusterBadgeSize,
                    );
                    ctx.fill();
                    const text = `${numGeoms}`;
                    const width = ctx.measureText(text).width;
                    ctx.fillStyle = mapStyles.assetClusterBadgeFont;
                    ctx.fillText(
                        text,
                        x + clusterBadgeOffset - width / 2.0,
                        y - clusterBadgeOffset,
                    );
                }
            },
        ),
    });
}

function createLayer() {
    return new VectorTileLayer({
        minZoom: signalAssetMinZoomInRatkoExtent,
        source: new VectorTileSource({
            format: new MVT(),
            url: `${API_URI}/ratko/signal-assets/{x}/{y}/{z}?cluster=true`,
            projection: LAYOUT_SRID,
            extent: ratkoTileLayerExtent,
        }),
        style: function (feature) {
            const ratkoAssetCluster = feature.getProperties() as RatkoMapAssetCluster;
            const names = ratkoAssetCluster.string_value.split(',');

            return [
                new Style({
                    renderer: signalImageRenderFunction,
                }),
                createSignalStyle(ratkoAssetCluster.num_geoms, names),
            ].filter(filterNotEmpty);
        },
    });
}

export function createSignalAssetLayer(existingOlLayer: Tile<TileSource>): MapLayer {
    const layer = existingOlLayer || createLayer();
    return {
        name: 'signal-asset-layer',
        layer: layer,
    };
}
