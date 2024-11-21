import React from 'react';
import { TopologicalConnectivityType } from 'track-layout/track-layout-model';
import { useTranslation } from 'react-i18next';

type TopologicalConnectivityLabelProps = {
    topologicalConnectivity: TopologicalConnectivityType;
};

const TopologicalConnectivityLabel: React.FC<TopologicalConnectivityLabelProps> = ({
    topologicalConnectivity,
}: TopologicalConnectivityLabelProps) => {
    const { t } = useTranslation();

    return (
        <React.Fragment>
            {t(`enum.TopologicalConnectivityType.${topologicalConnectivity}`)}
        </React.Fragment>
    );
};

export default TopologicalConnectivityLabel;
