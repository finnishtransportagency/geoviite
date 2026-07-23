import { LayoutDelegates } from 'store/hooks';

/**
 * Leaves alignment extension mode. The extension layer is only forced visible for the sake of the
 * mode, so it has to be released together with the linking state, whether the mode ends by
 * cancelling, by saving, or by the user placing an extension of no length at all.
 */
export const stopExtendingAlignment = (delegates: LayoutDelegates): void => {
    delegates.removeForcedVisibleLayer(['alignment-extension-layer']);
    delegates.stopLinking();
};
