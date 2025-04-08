package fi.fta.geoviite.infra.authorization

const val AUTH_BASIC = "hasAuthority('view-basic')"
const val AUTH_VIEW_LAYOUT = "hasAuthority('view-layout')"
const val AUTH_VIEW_LAYOUT_DRAFT = "hasAuthority('view-layout-draft')"
const val AUTH_EDIT_LAYOUT = "hasAuthority('edit-layout')"
const val AUTH_VIEW_GEOMETRY = "hasAuthority('view-geometry')"
const val AUTH_EDIT_GEOMETRY_FILE = "hasAuthority('edit-geometry-file')"
const val AUTH_DOWNLOAD_GEOMETRY = "hasAuthority('download-geometry')"
const val AUTH_VIEW_GEOMETRY_FILE = "hasAuthority('view-geometry-file')"
const val AUTH_VIEW_PUBLICATION = "hasAuthority('view-publication')"
const val AUTH_DOWNLOAD_PUBLICATION = "hasAuthority('download-publication')"
const val AUTH_VIEW_PV_DOCUMENTS = "hasAuthority('view-pv-documents')"

const val LAYOUT_BRANCH = "layoutBranch"
const val PUBLICATION_STATE = "publicationState"
const val AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE =
    "(#$PUBLICATION_STATE.name() == 'DRAFT' && $AUTH_VIEW_LAYOUT_DRAFT) || (#$PUBLICATION_STATE.name() == 'OFFICIAL' && $AUTH_VIEW_LAYOUT)"

const val AUTH_API_FRAME_CONVERTER = "hasAuthority('api-frame-converter')"
const val AUTH_API_GEOMETRY = "hasAuthority('api-geometry')"

// TODO How to name this?
const val TODO_FLAG_API_GEOMETRY = "api-geometry"
