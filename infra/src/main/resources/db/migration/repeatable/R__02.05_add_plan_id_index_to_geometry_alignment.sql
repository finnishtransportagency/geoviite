drop index if exists geometry.alignment_plan_ix;
create index alignment_plan_ix on geometry.alignment (plan_id);

