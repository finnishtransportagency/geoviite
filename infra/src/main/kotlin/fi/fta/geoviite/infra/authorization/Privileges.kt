package fi.fta.geoviite.infra.authorization

const val AUTH_BASIC = "hasAuthority('view-basic')"
const val AUTH_VIEW_LAYOUT = "hasAuthority('view-layout')"
const val AUTH_VIEW_LAYOUT_DRAFT = "hasAuthority('view-layout-draft')"
const val AUTH_EDIT_LAYOUT = "hasAuthority('edit-layout')"
const val AUTH_VIEW_GEOMETRY = "hasAuthority('view-geometry')"
const val AUTH_EDIT_GEOMETRY = "hasAuthority('edit-geometry')"
const val AUTH_DOWNLOAD_GEOMETRY = "hasAuthority('download-geometry')"
const val AUTH_VIEW_INFRAMODEL = "hasAuthority('view-inframodel')"
const val AUTH_VIEW_PUBLICATION = "hasAuthority('view-publication')"
const val AUTH_DOWNLOAD_PUBLICATION = "hasAuthority('download-publication')"
const val AUTH_VIEW_PV_DOCUMENTS = "hasAuthority('view-pv-documents')"

const val PUBLISH_TYPE = "publishType"
const val AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE = "(#$PUBLISH_TYPE.name() == 'DRAFT' && $AUTH_VIEW_LAYOUT_DRAFT) || (#$PUBLISH_TYPE.name() == 'OFFICIAL' && $AUTH_VIEW_LAYOUT)"
