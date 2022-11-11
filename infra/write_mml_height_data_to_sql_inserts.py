""" README

Description
------------
This program will create sql inserts for the Finnish triangular network points and triangles for Geoviite
in KKJ/ Finland Uniform Coordinate System (EPSG:2393)

Python environment
------------------
install python v. 3.8 or higher on your machine.
You can use local installation and optionally one of the following
pipenv https://github.com/pypa/pipenv,
pyenv https://github.com/pyenv/pyenv,
anaconda https://www.anaconda.com/,
venv https://docs.python.org/3/library/venv.html


Dependencies
-------------
pandas 1.3.3
i.e. pip install pandas==1.3.3

Running
-------
python write_mml_height_data_to_sql_inserts.py

"""

import pandas as pd

def comma(i,df):
    return "" if i == len(df) - 1 else ","

def fetch_mml_reference_points_for_height_systems_n60_n2000():
    """Get MML reference points for height systems n60-n2000."""
    url = r"https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/attachments/2019/02/n60n2000triangulationVertices.txt"  # pylint: disable=line-too-long
    return pd.read_csv(url, encoding="latin-1", delim_whitespace=True, header=None)

def fetch_mml_triangulation_network_for_height_systems_n60_n2000():
    """Get MML triangles for height systems n60-n2000."""
    url = r"https://www.maanmittauslaitos.fi/sites/maanmittauslaitos.fi/files/attachments/2019/02/n60n2000triangulationNetwork.txt"  # pylint: disable=line-too-long
    return pd.read_csv(url, encoding="latin-1", delim_whitespace=True, header=None)

def parse_n60_n2000_points(df):
    """Parse MML reference points for height systems n60-n2000."""
    df.columns = ["Id", "y", "x", "N60", "N2000"]
    df.index = df.iloc[:, 0]
    df["diff"] = df["N2000"] - df["N60"]
    df = df.loc[:, ["Id", "x", "y", "N60", "N2000", "diff"]]
    return df

def parse_n60_n2000_triangulation_network(df):
    """Parse MML triangles for height systems n60-n2000."""
    df.columns = ["coord1", "coord2", "coord3"]
    df.index = df.iloc[:, 0]
    df = df.loc[:, ["coord1", "coord2", "coord3"]]
    return df

def get_coordinate( point_id ,df_points):
    for index, row in df_points.iterrows():
        if(point_id == row['Id']):
            return row

def create_polygon(coord1, coord2, coord3, srid):
    polygon_str_start= "postgis.st_polygonfromtext('polygon("
    separator = " "
    point1 =  separator.join([str(coord1['x']),str(coord1['y'])])
    point2 = separator.join([str(coord2['x']),str(coord2['y'])])
    point3 = separator.join([str(coord3['x']),str(coord3['y'])])

    separator = ", "
    polygon_points = "( " + separator.join([point1, point2, point3, point1]) + " )"
    polygon_str_end = ")'," + srid + ")"
    return polygon_str_start + polygon_points + polygon_str_end

def create_polygon_coordinate_ids(coord1, coord2, coord3):
    coord1_id = "'" + coord1['Id'] + "'"
    coord2_id = "'" + coord2['Id'] + "'"
    coord3_id = "'" + coord3['Id'] + "'"
    separator = ", "
    return separator.join([coord1_id, coord2_id, coord3_id])

def create_values_for_triangle_corner_points(row, srid):
    separator = ", "
    coord_id = "'" + row['Id'] + "'"
    n60 = str(row['N60'])
    n2000 = str(row['N2000'])

    point_original = "postgis.st_pointfromtext('point(" + str(row['x']) + " " + str(row['y']) + ")', " + srid + ")"
    return separator.join([coord_id, n60, n2000, point_original])

def create_inserts_for_n60_n2000_triangulation_network_table(df_points, df_tri, srid):
    """Create inserts for common.n60_n2000_triangulation_network table.
    Coordinate systems is KKJ/ Finland Uniform Coordinate System (EPSG:2393).

    Parameters
    ----------
    df_points: array_like of shape (n, 2)
        points where to calculate the difference. Points in KKJ EPSG:2393.
    df_tri: array_like of shape (n, 2)
        triangle corner point lists. Point value references df_points id.
    srid: str
        Original SRID code. MML original EPSG:2393. Value 2393
    Returns
    -------
    void
        writes an output file of inserts for destination table
    """
    print("creating inserts for_triangulation_network_table")
    triangulation_network_file = open(
        "src/main/resources/db/migration/V10.04.03__common_inserts_for_n60_n2000_triangulation_network_table.sql", "w")

    insert_str = "insert into common.n60_n2000_triangulation_network(coord1_id, coord2_id, coord3_id, polygon_original) values"
    insert_end = ";"
    triangulation_network_file.write(insert_str + "\n")

    for i, (index, row) in enumerate(df_tri.iterrows()):
        coord1 = get_coordinate(row['coord1'],df_points)
        coord2 = get_coordinate(row['coord2'],df_points)
        coord3 = get_coordinate(row['coord3'],df_points)

        separator = ", "
        polygon = create_polygon(coord1, coord2, coord3, srid)
        coordinate_ids = create_polygon_coordinate_ids(coord1, coord2, coord3)

        line = "(" +  separator.join([ coordinate_ids, polygon ]) + ")" + comma(i, df_tri) + "\n"
        triangulation_network_file.write( line )

    triangulation_network_file.write(insert_end + "\n")
    triangulation_network_file.close()
    print('triangulation network inserts finished')



def create_inserts_for_n60_n2000_triangle_corner_point_table(df_points, srid):
    """Create inserts for common.n60_n2000_triangle_corner_point table.
    Coordinate system is KKJ/ Finland Uniform Coordinate System (EPSG:2393).

    Parameters
    ----------
    df_points: array_like of shape (n, 2)
        points where to calculate the difference. Points in KKJ EPSG:2393.
    srid: str
        Original SRID code. MML original EPSG:2393. Value 2393
    Returns
    -------
    void
        writes an output file of inserts for destination table
    """
    print("creating inserts_for_triangle_corner_point_table")
    triangle_corner_point_file = open(
        "src/main/resources/db/migration/V10.04.02__common_inserts_for_n60_n2000_triangle_corner_point_table.sql", "w")
    insert_str = "insert into common.n60_n2000_triangle_corner_point(coord_id, n60, n2000, point_original) values"
    insert_end = ";"

    triangle_corner_point_file.write(insert_str  + "\n")
    for i, (index, row) in enumerate(df_points.iterrows()):
        line = "(" +  create_values_for_triangle_corner_points(row, srid) + ")" + comma(i, df_points) + "\n"
        triangle_corner_point_file.write(line)

    triangle_corner_point_file.write(insert_end + "\n")
    triangle_corner_point_file.close()
    print("triangle corner point inserts finished")


def main():
    srid = '2393'

    df_points = parse_n60_n2000_points(fetch_mml_reference_points_for_height_systems_n60_n2000())
    df_tri = parse_n60_n2000_triangulation_network(fetch_mml_triangulation_network_for_height_systems_n60_n2000())

    create_inserts_for_n60_n2000_triangle_corner_point_table(df_points, srid)
    create_inserts_for_n60_n2000_triangulation_network_table(df_points, df_tri, srid)

    print("program finished")

if __name__ == "__main__":
    main()
