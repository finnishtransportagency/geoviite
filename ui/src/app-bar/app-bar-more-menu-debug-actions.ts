import {
    menuDivider,
    MenuDividerOption,
    menuOption,
    MenuSelectOption,
} from 'vayla-design-lib/menu/menu';
import { TFunction } from 'i18next/typescript/t';
import { getNonNull } from 'api/api-fetch';

type Translation = TFunction<'translation', undefined>;

export function createDebugActions(t: Translation): (MenuSelectOption | MenuDividerOption)[] {
    return [menuDivider(), debugActionExpiredToken(t)];
}

function debugActionExpiredToken(t: Translation): MenuSelectOption {
    return menuOption(
        () => {
            getNonNull<unknown>('/api/debug/token-expired').catch((_expectedRejection) => {
                // Already logged earlier to console, but unhandled rejection is caught here.
            });
        },
        t('app-bar-debug.expired-token'),
        'app-bar-debug.expired-token',
    );
}
