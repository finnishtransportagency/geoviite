import React from 'react';
import { PlanLinkingSummaryItem } from 'geometry/geometry-api';
import { OnSelectOptions } from 'selection/selection-model';
import { Coordinates } from 'vertical-geometry/coordinates';
import { PlanLinkingHeaderItem } from 'vertical-geometry/plan-linking-header-item';

export interface PlanLinkingHeaderProps {
    planLinkingSummary: PlanLinkingSummaryItem[] | undefined;
    planLinkingOnSelect: (options: OnSelectOptions) => void;
    coordinates: Coordinates;
}

export const PlanLinkingHeaders: React.FC<PlanLinkingHeaderProps> = ({
    planLinkingSummary,
    planLinkingOnSelect,
    coordinates,
}) => {
    return (
        <>
            {planLinkingSummary
                // Headers do not need to be drawn when there's no header information
                // available. This happens between points that do not have proper
                // vertical geometry data available and only a transfer line between
                // concrete vertical height points is drawn.
                ?.filter((summary) => summary.alignmentHeader)

                // Only summaries that are at least partially in the current view need to be drawn.
                .filter(
                    (summary) =>
                        summary.startM <= coordinates.endM && summary.endM >= coordinates.startM,
                )
                .map((summary, i) => (
                    <PlanLinkingHeaderItem
                        key={i}
                        coordinates={coordinates}
                        planLinkingSummaryItem={summary}
                        onSelect={planLinkingOnSelect}
                    />
                ))}
        </>
    );
};
