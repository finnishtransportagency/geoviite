-- increment this to force re-execution: 1
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int);
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int, int);

drop function if exists layout.km_post_is_in_layout_context(layout.publication_state, int, layout.km_post);

create function layout.km_post_is_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                    km_post layout.km_post) returns setof empty_type
  language sql
  stable as
$$
select
  where not km_post.cancelled
    and case publication_state_in
          when 'OFFICIAL' then not km_post.draft
          else km_post.draft
            or not exists(select *
                          from layout.km_post overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (km_post.design_id is null and overriding_draft.cancelled)
                            and overriding_draft.id = km_post.id)
        end
    and case
          when design_id_in is null then km_post.design_id is null
          else design_id_in = km_post.design_id
            or (km_post.design_id is null
              and not km_post.draft
              and not exists(select *
                               from layout.km_post overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.id = km_post.id
                                   and (publication_state_in = 'OFFICIAL' or not exists (
                                     select *
                                       from layout.km_post design_cancellation
                                       where design_cancellation.design_id = design_id_in
                                         and design_cancellation.draft
                                         and design_cancellation.cancelled
                                         and design_cancellation.id = km_post.id))))
        end
$$;

create function layout.km_post_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.km_post
  language sql
  stable
as
$$
select *
from layout.km_post,
  layout.km_post_is_in_layout_context(publication_state_in, design_id_in, km_post)
$$;
