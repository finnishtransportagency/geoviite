create table pgrouting.node
(
  id int primary key not null,
  location postgis.geometry(point, 3067) not null
);

create table pgrouting.edge
(
  id int primary key not null,
  length decimal(13, 6) not null,
  tracks int[] not null,
  start_node_id int not null references pgrouting.node(id),
  end_node_id int not null references pgrouting.node(id)
);
