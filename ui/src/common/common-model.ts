import { Point } from 'model/geometry';
import {
    AlignmentPoint,
    LayoutKmPostId,
    LayoutSwitchId,
    LayoutTrackNumberId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';
import { compare } from 'utils/array-utils';
import i18next from 'i18next';
import { brand, Brand } from 'common/brand';

export type RotationDirection = 'CW' | 'CCW';
export type LinearUnit = 'MILLIMETER' | 'CENTIMETER' | 'METER' | 'KILOMETER';
export type AngularUnit = 'RADIANS' | 'GRADS';

export type DataType = 'STORED' | 'TEMP';

export type LayoutDesignId = Brand<string, 'LayoutDesignId'>;
export type MainBranch = 'MAIN';
export type DesignBranch = Brand<string, 'DesignBranch'>;
export type LayoutBranch = MainBranch | DesignBranch;

export type PublicationState = 'OFFICIAL' | 'DRAFT';
export type LayoutContext = {
    publicationState: PublicationState;
    branch: LayoutBranch;
};

export const designBranch = (designId: LayoutDesignId): DesignBranch => brand(`DESIGN_${designId}`);

export type LayoutContextMode = 'MAIN_OFFICIAL' | 'MAIN_DRAFT' | 'DESIGN';

export const officialLayoutContext = (layoutContext: LayoutContext): LayoutContext =>
    layoutContext.publicationState === 'OFFICIAL'
        ? layoutContext
        : {
              publicationState: 'OFFICIAL',
              branch: layoutContext.branch,
          };

export const draftLayoutContext = (layoutContext: LayoutContext): LayoutContext =>
    layoutContext.publicationState === 'DRAFT'
        ? layoutContext
        : {
              publicationState: 'DRAFT',
              branch: layoutContext.branch,
          };

const officialMainContext: LayoutContext = Object.freeze({
    publicationState: 'OFFICIAL',
    branch: 'MAIN',
});
const draftMainContext: LayoutContext = Object.freeze({
    publicationState: 'DRAFT',
    branch: 'MAIN',
});

export const officialMainLayoutContext = (): LayoutContext => officialMainContext;
export const draftMainLayoutContext = (): LayoutContext => draftMainContext;

export function draftDesignLayoutContext(designId: LayoutDesignId): LayoutContext {
    return {
        publicationState: 'DRAFT',
        branch: designBranch(designId),
    };
}

export type LayoutMode = 'DEFAULT' | 'PREVIEW';

export type LocationAccuracy =
    | 'DESIGNED_GEOLOCATION'
    | 'OFFICIALLY_MEASURED_GEODETICALLY'
    | 'MEASURED_GEODETICALLY'
    | 'DIGITIZED_AERIAL_IMAGE'
    | 'GEOMETRY_CALCULATED';

export type MeasurementMethod =
    | 'VERIFIED_DESIGNED_GEOMETRY'
    | 'OFFICIALLY_MEASURED_GEODETICALLY'
    | 'TRACK_INSPECTION'
    | 'DIGITIZED_AERIAL_IMAGE'
    | 'UNVERIFIED_DESIGNED_GEOMETRY';

export type ElevationMeasurementMethod = 'TOP_OF_SLEEPER' | 'TOP_OF_RAIL';

export type TrackNumber = Brand<string, 'TrackNumber'>;

export type KmNumber = string;
export const ZERO_TRACK_METER: TrackMeter = {
    kmNumber: '0000',
    meters: 0.0,
};
export type TrackMeter = {
    kmNumber: KmNumber;
    meters: number;
};

export type AssetId =
    | LayoutTrackNumberId
    | ReferenceLineId
    | LocationTrackId
    | LayoutSwitchId
    | LayoutKmPostId;

const TRACK_METER_REGEX = /([0-9]{1,4})([a-zA-Z]{0,2})\+([0-9]{4}(?:.[0-9]{1,4})?)$/;

export const trackMeterIsValid = (trackMeter: string) => TRACK_METER_REGEX.test(trackMeter);

const splitTrackMeterIntoComponents = (trackMeterString: string) => {
    const components = trackMeterString.match(TRACK_METER_REGEX);
    
    return {
        kms: components?.[1]?.padStart(4, '0'),
        letters: components?.[2],
        meters: components?.[3],
    };
};

export const compareTrackMeterStrings = (a: string, b: string) => {
    const aComponents = splitTrackMeterIntoComponents(a);
    const bComponents = splitTrackMeterIntoComponents(b);

    const kmDiff = compare(aComponents.kms, bComponents.kms);
    const letterDiff = compare(aComponents.letters, bComponents.letters);
    const meterDiff = compare(aComponents.meters, bComponents.meters);

    return kmDiff !== 0 ? kmDiff : letterDiff !== 0 ? letterDiff : meterDiff;
};

export const compareNamed = (a: { name: string | undefined }, b: { name: string | undefined }) =>
    a?.name?.localeCompare(b?.name || '', i18next.language) || 0;

export type AddressPoint = {
    point: AlignmentPoint;
    address: TrackMeter;
    distance: number;
};

export type ElementLocation = {
    coordinate: Point;
    address?: TrackMeter;
    directionGrads: number;
    radiusMeters?: number;
    cant?: number;
};

export type LayoutAssetChangeInfo = {
    created: TimeStamp;
    changed: TimeStamp | undefined;
};

export type Message = string;

export type SwitchHand = 'RIGHT' | 'LEFT' | 'NONE';
export type SwitchBaseType =
    | 'YV'
    | 'KV'
    | 'KRV'
    | 'YRV'
    | 'RR'
    | 'SRR'
    | 'TYV'
    | 'UKV'
    | 'SKV'
    | 'EV';
export type JointNumber = string;

export type Oid = string;
export type Srid = string;
export type SwitchStructureId = string;
export type SwitchAlignmentId = string;
export type VerticalCoordinateSystem = string;
export type SwitchOwnerId = string;
export type LocationTrackOwnerId = string;

export type CoordinateSystem = {
    srid: Srid;
    name: string;
    aliases: string[];
};

export type SwitchType = string;

export type SwitchTypeJoint = {
    number: JointNumber;
    location: Point;
};

export type SwitchElement = {
    start: Point;
    end: Point;
};

export type SwitchAlignment = {
    id: SwitchAlignmentId;
    jointNumbers: JointNumber[];
    elements: SwitchElement[];
};

export type SwitchStructure = {
    id: SwitchStructureId;
    type: SwitchType;
    hand: SwitchHand;
    baseType: SwitchBaseType;
    presentationJointNumber: JointNumber;
    joints: SwitchTypeJoint[];
    alignments: SwitchAlignment[];
};

export type SwitchOwner = {
    id: string;
    name: string;
};

export type LocationTrackOwner = {
    id: string;
    name: string;
};

export type RowVersion = string;

export enum LayoutEndPoint {
    SWITCH = 'SWITCH', // vaihde
    LOCATION_TRACK = 'LOCATION_TRACK',
    ENDPOINT = 'ENDPOINT', // muu
    /*
    KAANTOPOYTA = 'KAANTOPOYTA',
    SEISLEVY = 'SEISLEVY',
    HALLI = 'HALLI',
    ASEMA = 'ASEMA',
    KISKON_PAA = 'KISKON_PAA',
    OMISTUSRAJA = 'OMISTUSRAJA',
    LIIKENNOINNIN_RAJA = 'LIIKENNOININ_RAJA',
    VAIHDEPIIRIN_RAJA = 'VAIHDEPIIRIN_RAJA',
    LIIKENNEPAIKAN_RAJA = 'LIIKENNEPAIKAN_RAJA',
    VALTAKUNNAN_RAJA = 'VALTAKUNNAN_RAJA',
    HAAPARANTA = 'HAAPARANTA',
    */
}

export type TimeStamp = string;

export type Range<T> = {
    min: T;
    max: T;
};
