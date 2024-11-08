package fi.fta.geoviite.infra.cloudfront

data class CloudFrontCookies(val policy: String, val signature: String, val keyPairId: String, val domain: String)
