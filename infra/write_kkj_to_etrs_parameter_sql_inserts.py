import pandas as pd;


def fetch_kkj_to_etrs_triangulation_vertices():
    """Download triangulation network triangle definitions."""
    url = r"https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/attachments/2019/02/kkjEUREFFINtriangulationVertices.txt"  # pylint: disable=line-too-long
    return pd.read_csv(url, encoding="latin-1", delim_whitespace=True, header=None, dtype=str)


def fetch_kkj_to_etrs_affine_transform_paramteres():
    """Get affine parameters for triangles."""
    url = r"https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/attachments/2019/02/KKJ_TO_ETRS_TM35FIN.txt"  # pylint: disable=line-too-long
    return pd.read_csv(url, encoding="latin-1", delim_whitespace=True, header=None, dtype=str)

def fetch_etrs_to_kkj_affine_transform_paramteres():
    """Get affine parameters for triangles."""
    url = r"https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/attachments/2019/02/ETRS_TM35FIN_TO_KKJ.txt"  # pylint: disable=line-too-long
    return pd.read_csv(url, encoding="latin-1", delim_whitespace=True, header=None, dtype=str)


def parse_vertices(df_vertices):
    df_vertices.columns = ["Id", "kkj_n", "kkj_e", "etrs_n", "etrs_e"]
    df_vertices.index = df_vertices.iloc[:, 0]
    df_vertices = df_vertices.loc[:, ["Id", "kkj_n", "kkj_e", "etrs_n", "etrs_e"]]
    return df_vertices


def parse_affine_parameters(df_affine_parameters):
    df_affine_parameters.columns = ["point1", "point2", "point3", "triangle_id", "a1", "a2", "delta_e", "b1", "b2",
                                    "delta_n"]
    df_affine_parameters.index = df_affine_parameters.iloc[:, 0]
    df_affine_parameters = df_affine_parameters.loc[:,
                           ["point1", "point2", "point3", "triangle_id", "a1", "a2", "delta_e", "b1", "b2", "delta_n"]]
    return df_affine_parameters


def create_inserts_for_triangle_corner_points(df_vertices):
    print("Creating inserts for kkj_etrs_triangle_corner_point")
    file = open("V10.05.02__common_inserts_for_kkj_etrs_triangle_corner_point_table.sql", "w")
    insert_str = "insert into common.kkj_etrs_triangle_corner_point(id, coord_kkj, coord_etrs) values"
    file.write(insert_str + "\n")
    value_lines_str = []

    for i, (index, row) in enumerate(df_vertices.iterrows()):
        kkj_point_str = "postgis.st_setsrid(postgis.st_point({kkj_e}, {kkj_n}), 2393)".format(kkj_n=row["kkj_n"],
                                                                                              kkj_e=row["kkj_e"])
        etrs_point_str = "postgis.st_setsrid(postgis.st_point({etrs_n}, {etrs_e}), 3067)".format(etrs_n=row["etrs_n"],
                                                                                                 etrs_e=row["etrs_e"])

        line = "({id}, {kkj_point}, {etrs_point})".format(id=row["Id"], kkj_point=kkj_point_str,
                                                          etrs_point=etrs_point_str)
        value_lines_str.append(line)

    values_str = ",\n".join(value_lines_str)
    file.write("{values};\n".format(values=values_str))
    print("Corner point inserts finished!")


def create_inserts_for_affine_parameters(df_affine_parameters, direction):
    print("Creating inserts for kkj_etrs_triangulation_network")
    file = open("V10.05.03__common_inserts_for_{direction}_triangulation_network.sql".format(direction=direction), "w")
    insert_str = "insert into common.kkj_etrs_triangulation_network(coord1_id, coord2_id, coord3_id, a1, a2, delta_e, b1, b2, delta_n, direction) values"
    value_lines_str = []

    for i, (index, row) in enumerate(df_affine_parameters.iterrows()):
        line = "({coord1_id}, {coord2_id}, {coord3_id}, {a1}, {a2}, {delta_e}, {b1}, {b2}, {delta_n}, '{direction}')".format(
            coord1_id=row["point1"],
            coord2_id=row["point2"],
            coord3_id=row["point3"],
            a1=row["a1"],
            a2=row["a2"],
            delta_e=row["delta_e"],
            b1=row["b1"],
            b2=row["b2"],
            delta_n=row["delta_n"],
            direction=direction
        )
        value_lines_str.append(line)

    values_str = ",\n".join(value_lines_str)
    file.write(insert_str + "\n")
    file.write("{values};\n".format(values=values_str))
    print("Triangulation network inserts finished!")


def main():
    print("Downloading input data")
    df_vertices = parse_vertices(fetch_kkj_to_etrs_triangulation_vertices())
    df_affine_params_kkj_to_etrs = parse_affine_parameters(fetch_kkj_to_etrs_affine_transform_paramteres())
    df_affine_params_etrs_to_kkj = parse_affine_parameters(fetch_etrs_to_kkj_affine_transform_paramteres())
    print("Downloaded. Creating SQL Files next")
    create_inserts_for_triangle_corner_points(df_vertices)
    create_inserts_for_affine_parameters(df_affine_params_kkj_to_etrs, "KKJ_TO_TM35FIN")
    create_inserts_for_affine_parameters(df_affine_params_etrs_to_kkj, "TM35FIN_TO_KKJ")
    print("All done.")


if __name__ == "__main__":
    main()
