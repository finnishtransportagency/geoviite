package fi.fta.geoviite.infra.velho.projektivelho

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(ProjektiVelhoClientConfiguration::class)
class ProjektiVelhoService @Autowired constructor(
    private val projektiVelhoClient: ProjektiVelhoClient
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun login() = projektiVelhoClient.fetchAccessToken()
    fun fetch() = projektiVelhoClient.fetchFilesFromVelho()
    fun latest() = projektiVelhoClient.fetchLatest()
}
