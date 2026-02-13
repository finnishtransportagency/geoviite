import React from 'react';
import { useTranslation } from 'react-i18next';
import Overlay from 'ol/Overlay';
import OlMap from 'ol/Map';
import { LinkingClusterPoint, LinkPoint } from 'linking/linking-model';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { exhaustiveMatchingGuard } from 'utils/type-utils';
import styles from './map-overlay.scss';

type ClickType = 'all' | 'geometryPoint' | 'layoutPoint' | 'remove';

type AlignmentLinkingClusterOverlayProps = {
    olMap: OlMap | undefined;
    clusterPoint: LinkingClusterPoint | undefined;
    onSetLayoutClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onSetGeometryClusterLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveLayoutLinkPoint: (linkPoint: LinkPoint) => void;
    onRemoveGeometryLinkPoint: (linkPoint: LinkPoint) => void;
};

export const AlignmentLinkingClusterOverlay: React.FC<AlignmentLinkingClusterOverlayProps> = ({
    olMap,
    clusterPoint,
    onSetLayoutClusterLinkPoint,
    onSetGeometryClusterLinkPoint,
    onRemoveLayoutLinkPoint,
    onRemoveGeometryLinkPoint,
}) => {
    const { t } = useTranslation();

    const handleClusterPointClick = (clickType: ClickType) => {
        if (clusterPoint) {
            switch (clickType) {
                case 'all':
                    onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'geometryPoint':
                    onSetGeometryClusterLinkPoint(clusterPoint.geometryPoint);
                    onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                    break;
                case 'layoutPoint':
                    onSetLayoutClusterLinkPoint(clusterPoint.layoutPoint);
                    onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    break;
                case 'remove':
                    onRemoveLayoutLinkPoint(clusterPoint.layoutPoint);
                    onRemoveGeometryLinkPoint(clusterPoint.geometryPoint);
                    break;
                default:
                    return exhaustiveMatchingGuard(clickType);
            }
        }
    };

    React.useEffect(() => {
        if (!olMap || !clusterPoint) return;
        const pos = pointToCoords(clusterPoint);
        const popupElement = document.getElementById('clusteroverlay') || undefined;
        const popup = new Overlay({
            position: pos,
            offset: [7, 0],
            element: popupElement,
        });
        olMap.addOverlay(popup);
    }, [olMap, clusterPoint]);

    return (
        <div id="clusteroverlay">
            {clusterPoint && (
                <div className={styles['map-overlay__popup-menu']}>
                    <div
                        className={styles['map-overlay__popup-item']}
                        onClick={() => handleClusterPointClick('geometryPoint')}>
                        {t('map-view.cluster-overlay-choose-geometry')}
                    </div>
                    <div
                        className={styles['map-overlay__popup-item']}
                        onClick={() => handleClusterPointClick('layoutPoint')}>
                        {t('map-view.cluster-overlay-choose-layout')}
                    </div>
                    <div
                        className={styles['map-overlay__popup-item']}
                        onClick={() => handleClusterPointClick('all')}>
                        {t('map-view.cluster-overlay-choose-both')}
                    </div>
                    <div
                        className={styles['map-overlay__popup-item']}
                        onClick={() => handleClusterPointClick('remove')}>
                        {t('map-view.cluster-overlay-remove-both')}
                    </div>
                </div>
            )}
        </div>
    );
};
