import { Point } from 'model/geometry';
import {
    LayoutPoint,
    LayoutSwitchId,
    LocationTrackId,
    ReferenceLineId,
} from 'track-layout/track-layout-model';

export type RotationDirection = 'CW' | 'CCW';
export type LinearUnit = 'MILLIMETER' | 'CENTIMETER' | 'METER' | 'KILOMETER';
export type AngularUnit = 'RADIANS' | 'GRADS';

export type DataType = 'STORED' | 'TEMP';

export type PublishType = 'OFFICIAL' | 'DRAFT';
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

export type TrackNumber = string;

export type KmNumber = string;
export const ZERO_TRACK_METER: TrackMeter = {
    kmNumber: '0000',
    meters: 0.0,
};
export type TrackMeter = {
    kmNumber: KmNumber;
    meters: number;
};

export type AddressPoint = {
    point: LayoutPoint;
    address: TrackMeter;
    distance: number;
};

export type ChangeTimes = {
    changed: Date;
    created: Date;
};

export type Message = string;

export type SwitchHand = 'RIGHT' | 'LEFT' | 'NONE';
export type SwitchBaseType = 'YV' | 'KV' | 'KRV' | 'YRV' | 'RR' | 'SRR' | 'TYV' | 'UKV' | 'SKV';
export type JointNumber = string;

export type Oid = string;
export type Srid = string;
export type SwitchStructureId = string;
export type SwitchAlignmentId = string;
export type VerticalCoordinateSystem = string;
export type SwitchOwnerId = string;

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

export type RowVersion = {
    id: string;
    version: number;
};

export enum LocationTrackPointUpdateType {
    END_POINT = 'END_POINT',
    START_POINT = 'START_POINT',
}

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

export type LocationTrackEndPoint = {
    type: LayoutEndPoint;
    alignmentId: LocationTrackId | ReferenceLineId;
    switchId: LayoutSwitchId;
};

export type TimeStamp = string;
