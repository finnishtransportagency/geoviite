import * as React from 'react';
import { ToolBar } from 'tool-bar/tool-bar';
import { useTrackLayoutAppSelector } from 'store/hooks';
import { createDelegates } from 'store/store-utils';
import { trackLayoutActionCreators } from 'track-layout/track-layout-slice';

export const ToolBarContainer: React.FC = () => {
    const trackLayoutState = useTrackLayoutAppSelector((state) => state);
    const delegates = React.useMemo(() => createDelegates(trackLayoutActionCreators), []);

    return (
        <ToolBar
            layoutContext={trackLayoutState.layoutContext}
            layoutContextMode={trackLayoutState.layoutContextMode}
            onLayoutContextModeChange={delegates.onLayoutContextModeChange}
            designId={trackLayoutState.designId}
            onDesignIdChange={delegates.onDesignIdChange}
            showArea={delegates.showArea}
            onSelect={delegates.onSelect}
            onUnselect={delegates.onUnselect}
            setToolPanelTab={delegates.setToolPanelTab}
            onOpenPreview={() => delegates.onLayoutModeChange('PREVIEW')}
            onStopLinking={delegates.stopLinking}
            linkingState={trackLayoutState.linkingState}
            splittingState={trackLayoutState.splittingState}
        />
    );
};
