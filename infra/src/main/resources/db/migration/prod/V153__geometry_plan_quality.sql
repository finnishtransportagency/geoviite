-- Add new measurement method values (additive — cannot remove old values from a postgres enum)
alter type common.measurement_method add value 'POINT_CLOUD_SIGNALED';
alter type common.measurement_method add value 'POINT_CLOUD_UNSIGNALED';
alter type common.measurement_method add value 'GNSS_IMU';
alter type common.measurement_method add value 'RTK_GNSS';

-- Add OUTDATED lifecycle state for geometry plans
alter type geometry.plan_decision add value 'OUTDATED';

-- Add GEOVIITE as a plan source for directly uploaded plans
alter type geometry.plan_source add value 'GEOVIITE';
