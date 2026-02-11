import React from 'react';
import Overlay from 'ol/Overlay';
import OlMap from 'ol/Map';
import { OperationalPointClusterPoint } from 'linking/linking-model';
import { OperationalPointId } from 'track-layout/track-layout-model';
import { OnSelectFunction } from 'selection/selection-model';
import { pointToCoords } from 'map/layers/utils/layer-utils';
import { expectDefined } from 'utils/type-utils';
import styles from './operational-point-cluster-overlay.module.scss';

type OperationalPointClusterOverlayProps = {
    olMap: OlMap | undefined;
    cluster: OperationalPointClusterPoint | undefined;
    onSelect: OnSelectFunction;
};

export const OperationalPointClusterOverlay: React.FC<OperationalPointClusterOverlayProps> = ({
    olMap,
    cluster,
    onSelect,
}) => {
    const handleOperationalPointClick = (id: OperationalPointId) => {
        onSelect({
            operationalPoints: [id],
            operationalPointClusters: [],
            selectedTab: {
                type: 'OPERATIONAL_POINT',
                id,
            },
        });
    };

    React.useEffect(() => {
        if (!olMap || !cluster) return;
        const pos = pointToCoords(cluster);
        const popupElement = document.getElementById('operationalpointclusteroverlay') || undefined;
        const popup = new Overlay({
            position: pos,
            offset: [7, 0],
            element: popupElement,
        });
        olMap.addOverlay(popup);
    }, [olMap, cluster]);

    return (
        <div id="operationalpointclusteroverlay">
            {cluster && (
                <div className={styles['operational-point-cluster-overlay__popup-menu']}>
                    {expectDefined(cluster).operationalPoints.map((point) => (
                        <div
                            key={point.id}
                            className={styles['operational-point-cluster-overlay__popup-item']}
                            onClick={() => handleOperationalPointClick(point.id)}>
                            {point.name}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};
