import { PayloadAction } from '@reduxjs/toolkit';

export type PreviewState = {
    showOnlyOwnUnstagedChanges: boolean;
};

export const previewReducers = {
    setShowOnlyOwnUnstagedChanges: function (
        state: PreviewState,
        action: PayloadAction<boolean>,
    ): void {
        state.showOnlyOwnUnstagedChanges = action.payload;
    },
};
