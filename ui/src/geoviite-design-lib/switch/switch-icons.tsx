import { IconComponent, makeHigherOrderSvgIcon } from 'vayla-design-lib/icon/Icon';
import { SwitchBaseType, SwitchHand } from 'common/common-model';
import switchYvVSvg from 'geoviite-design-lib/glyphs/switch-small/switch_yv_v.svg';
import switchYvOSvg from 'geoviite-design-lib/glyphs/switch-small/switch_yv_o.svg';
import switchKvVSvg from 'geoviite-design-lib/glyphs/switch-small/switch_kv_v.svg';
import switchKvOSvg from 'geoviite-design-lib/glyphs/switch-small/switch_kv_o.svg';
import switchUkvVSvg from 'geoviite-design-lib/glyphs/switch-small/switch_ukv_v.svg';
import switchUkvOSvg from 'geoviite-design-lib/glyphs/switch-small/switch_ukv_o.svg';
import switchSkvVSvg from 'geoviite-design-lib/glyphs/switch-small/switch_skv_v.svg';
import switchSkvOSvg from 'geoviite-design-lib/glyphs/switch-small/switch_skv_o.svg';
import switchKrvSvg from 'geoviite-design-lib/glyphs/switch-small/switch_krv.svg';
import switchSrrSvg from 'geoviite-design-lib/glyphs/switch-small/switch_srr.svg';
import switchRrSvg from 'geoviite-design-lib/glyphs/switch-small/switch_rr.svg';
import switchTyvSvg from 'geoviite-design-lib/glyphs/switch-small/switch_tyv.svg';
import switchYrvSvg from 'geoviite-design-lib/glyphs/switch-small/switch_yrv.svg';
import switchYvVSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_yv_v.svg';
import switchYvOSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_yv_o.svg';
import switchKvVSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_kv_v.svg';
import switchKvOSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_kv_o.svg';
import switchUkvVSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_ukv_v.svg';
import switchUkvOSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_ukv_o.svg';
import switchSkvVSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_skv_v.svg';
import switchSkvOSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_skv_o.svg';
import switchKrvSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_krv.svg';
import switchSrrSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_srr.svg';
import switchTyvSvgLarge from 'geoviite-design-lib/glyphs/switch-large/switch_tyv.svg';
import { exhaustiveMatchingGuard } from 'utils/type-utils';

function getSwitchSvg(type: SwitchBaseType, hand: SwitchHand | undefined): string {
    switch (type) {
        case 'YV':
        case 'EV':
            return hand === 'LEFT' ? switchYvVSvg : switchYvOSvg;
        case 'KV':
            return hand === 'LEFT' ? switchKvVSvg : switchKvOSvg;
        case 'KRV':
            return switchKrvSvg;
        case 'YRV':
            return switchYrvSvg;
        case 'RR':
            return switchRrSvg;
        case 'SRR':
            return switchSrrSvg;
        case 'TYV':
            return switchTyvSvg;
        case 'UKV':
            return hand === 'LEFT' ? switchUkvVSvg : switchUkvOSvg;
        case 'SKV':
            return hand === 'LEFT' ? switchSkvVSvg : switchSkvOSvg;
        default:
            return exhaustiveMatchingGuard(type);
    }
}

function getSwitchLargeSvg(type: SwitchBaseType, hand: SwitchHand | undefined): string {
    switch (type) {
        case 'YV':
        case 'EV':
            return hand === 'LEFT' ? switchYvVSvgLarge : switchYvOSvgLarge;
        case 'KV':
            return hand === 'LEFT' ? switchKvVSvgLarge : switchKvOSvgLarge;
        case 'KRV':
        case 'RR': // TODO: No separate RR icon
        case 'YRV': // TODO: No separate YRV icon
            return switchKrvSvgLarge;
        case 'SRR':
            return switchSrrSvgLarge;
        case 'TYV':
            return switchTyvSvgLarge;
        case 'UKV':
            return hand === 'LEFT' ? switchUkvVSvgLarge : switchUkvOSvgLarge;
        case 'SKV':
            return hand === 'LEFT' ? switchSkvVSvgLarge : switchSkvOSvgLarge;
        default:
            return exhaustiveMatchingGuard(type);
    }
}

export function makeSwitchImage(type: SwitchBaseType, hand: SwitchHand | undefined): IconComponent {
    return makeHigherOrderSvgIcon(getSwitchLargeSvg(type, hand));
}

export function makeSwitchIcon(type: SwitchBaseType, hand: SwitchHand | undefined): IconComponent {
    return makeHigherOrderSvgIcon(getSwitchSvg(type, hand));
}
