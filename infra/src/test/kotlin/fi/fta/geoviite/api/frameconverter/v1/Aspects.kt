import fi.fta.geoviite.infra.InfraApplication
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "integration-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
annotation class GeoviiteIntegrationApiTest
