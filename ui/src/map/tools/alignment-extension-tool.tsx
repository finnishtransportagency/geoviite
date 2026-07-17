import React from 'react';
import { Draw } from 'ol/interaction';
import { Point as OlPoint } from 'ol/geom';
import { FeatureLike } from 'ol/Feature';
import { MapToolHandle, MapToolWithButton } from 'map/tools/tool-model';
import { ExtendingAlignment, LinkingState, LinkingType } from 'linking/linking-model';
import { coordsToPoint, Point } from 'model/geometry';
import { AlignmentStartAndEnd } from 'track-layout/track-layout-model';
import { getChangeTimes } from 'common/change-time-api';
import { MapToolButton } from 'map/tools/map-tool-button';
import { Icons } from 'vayla-design-lib/icon/Icon';
import {
    AlignmentEnd,
    extensionLocation,
    getAlignmentStartAndEnd,
    nearestAlignmentEnd,
} from 'map/layers/utils/alignment-extension-layer-utils';
import { extensionSketchStyle } from 'map/layers/alignment-extension-layer';
import { distance } from 'utils/math-utils';

const id = 'alignment-extension';

// Avoid messy internal errors from saving the extension segment, which must also have a minimum length.
const MINIMUM_EXTENSION_LENGTH = 0.001;

const extendingAlignment = (
    linkingState: LinkingState | undefined,
): ExtendingAlignment | undefined =>
    linkingState?.type === LinkingType.ExtendingAlignment ? linkingState : undefined;

const shouldDrawBeActive = (linkingState: LinkingState | undefined): boolean => {
    const state = extendingAlignment(linkingState);
    return state !== undefined && state.extension === undefined;
};

const sketchCursor = (feature: FeatureLike): Point | undefined => {
    const geometry = feature.getGeometry();
    return geometry instanceof OlPoint ? coordsToPoint(geometry.getCoordinates()) : undefined;
};

export const alignmentExtensionTool: MapToolWithButton = {
    id,
    customCursor: (linkingState: LinkingState | undefined) =>
        shouldDrawBeActive(linkingState) ? 'crosshair' : undefined,
    activate: (map, _, options): MapToolHandle => {
        const alignment = extendingAlignment(options.linkingState)?.alignment;
        const onSetAlignmentExtension = options.onSetAlignmentExtension;
        const onStopExtendingAlignment = options.onStopExtendingAlignment;

        let startAndEnd: AlignmentStartAndEnd | undefined = undefined;
        let directionSnap = extendingAlignment(options.linkingState)?.directionSnap ?? false;
        let activated = true;

        if (alignment !== undefined) {
            getAlignmentStartAndEnd(alignment, options.layoutContext, getChangeTimes()).then(
                (result) => {
                    if (activated) {
                        startAndEnd = result;
                    }
                },
            );
        }

        const resolveExtension = (
            cursor: Point | undefined,
        ): { end: AlignmentEnd; from: Point; to: Point } | undefined => {
            const nearest =
                cursor === undefined ? undefined : nearestAlignmentEnd(startAndEnd, cursor);
            return cursor === undefined || nearest === undefined
                ? undefined
                : {
                      end: nearest,
                      from: nearest.location,
                      to: extensionLocation(nearest, cursor, directionSnap),
                  };
        };

        const draw = new Draw({
            type: 'Point',
            style: (feature) => {
                const resolved = resolveExtension(sketchCursor(feature));
                return resolved === undefined ||
                    // avoid drawing a weird little nub when the extension is empty
                    distance(resolved.from, resolved.to) < MINIMUM_EXTENSION_LENGTH
                    ? undefined
                    : extensionSketchStyle(resolved.from, resolved.to);
            },
        });

        draw.on('drawend', (event) => {
            const resolved = resolveExtension(sketchCursor(event.feature));
            if (resolved === undefined) {
                return;
            } else if (distance(resolved.from, resolved.to) < MINIMUM_EXTENSION_LENGTH) {
                onStopExtendingAlignment();
            } else {
                onSetAlignmentExtension({ end: resolved.end.end, location: resolved.to });
            }
        });

        draw.setActive(shouldDrawBeActive(options.linkingState));
        map.addInteraction(draw);

        return {
            deactivate: () => {
                activated = false;
                map.removeInteraction(draw);
            },
            onLinkingStateChanged: (linkingState) => {
                directionSnap = extendingAlignment(linkingState)?.directionSnap ?? directionSnap;
                draw.setActive(shouldDrawBeActive(linkingState));
            },
        };
    },
    component: ({ isActive, setActiveTool, disabled, hidden }) => {
        return (
            <MapToolButton
                id={id}
                isActive={isActive}
                setActive={setActiveTool}
                icon={Icons.Add}
                disabled={disabled}
                hidden={hidden}
            />
        );
    },
};
